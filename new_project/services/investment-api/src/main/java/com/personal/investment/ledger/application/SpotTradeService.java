package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.BalancedPostings;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FifoAllocation;
import com.personal.investment.ledger.domain.FifoCostAllocator;
import com.personal.investment.ledger.domain.FifoLot;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerPostingTemplates;
import com.personal.investment.ledger.domain.LedgerTradeDetail;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.LedgerSourceType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.PositionEffect;
import com.personal.investment.ledger.domain.Posting;
import com.personal.investment.ledger.domain.PostingSide;
import com.personal.investment.ledger.domain.Quantity;
import com.personal.investment.ledger.domain.SpotTradeMath;
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import com.personal.investment.ledger.domain.TradableInstrument;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S1 command service for exact native-currency EQUITY/ETF FIFO buys and sells. */
@Service
public class SpotTradeService {
  private final LedgerCommandAccountPort accountLookupPort;
  private final LedgerAccountPort accountMutationPort;
  private final LedgerTransactionPort transactionPort;
  private final SpotInstrumentPort instrumentPort;
  private final SpotLotPort lotPort;
  private final LedgerIdGenerator idGenerator;
  private final LedgerTransactionEventPort transactionEventPort;
  private final SpotHistoryReplayer historyReplayer;

  public SpotTradeService(LedgerCommandAccountPort accountLookupPort, LedgerAccountPort accountMutationPort,
      LedgerTransactionPort transactionPort, SpotInstrumentPort instrumentPort, SpotLotPort lotPort,
      LedgerIdGenerator idGenerator) {
    this(accountLookupPort, accountMutationPort, transactionPort, instrumentPort, lotPort, idGenerator,
        LedgerTransactionEventPort.noop(), SpotHistoryReplayer.noop());
  }

  public SpotTradeService(LedgerCommandAccountPort accountLookupPort, LedgerAccountPort accountMutationPort,
      LedgerTransactionPort transactionPort, SpotInstrumentPort instrumentPort, SpotLotPort lotPort,
      LedgerIdGenerator idGenerator, LedgerTransactionEventPort transactionEventPort) {
    this(accountLookupPort, accountMutationPort, transactionPort, instrumentPort, lotPort, idGenerator,
        transactionEventPort, SpotHistoryReplayer.noop());
  }

  @Autowired
  public SpotTradeService(LedgerCommandAccountPort accountLookupPort, LedgerAccountPort accountMutationPort,
      LedgerTransactionPort transactionPort, SpotInstrumentPort instrumentPort, SpotLotPort lotPort,
      LedgerIdGenerator idGenerator, LedgerTransactionEventPort transactionEventPort,
      SpotHistoryReplayer historyReplayer) {
    this.accountLookupPort = accountLookupPort;
    this.accountMutationPort = accountMutationPort;
    this.transactionPort = transactionPort;
    this.instrumentPort = instrumentPort;
    this.lotPort = lotPort;
    this.idGenerator = idGenerator;
    this.transactionEventPort = transactionEventPort;
    this.historyReplayer = historyReplayer;
  }

  @Transactional
  public SpotTradeResult buy(String ownerUserId, SpotTradeCommand command) {
    return buy(ownerUserId, command, null, LedgerSourceType.MANUAL, null);
  }

  /** Appends a buy reconstructed from a confirmed import preview. */
  @Transactional
  public SpotTradeResult buyImported(String ownerUserId, SpotTradeCommand command, String importExportFileId) {
    return buy(ownerUserId, command, null, LedgerSourceType.IMPORT, importExportFileId);
  }

  @Transactional
  public SpotTradeResult buyReplacement(LedgerAppendContext context, SpotTradeCommand command) {
    Objects.requireNonNull(context, "context must not be null");
    return buy(context.ownerUserId(), command, context, LedgerSourceType.CORRECTION_REPLACEMENT, null);
  }

  private SpotTradeResult buy(String ownerUserId, SpotTradeCommand command, LedgerAppendContext context,
      LedgerSourceType sourceType, String importExportFileId) {
    validateCommand(ownerUserId, command);
    LedgerAccount cash = activeCashAccount(ownerUserId, command.cashAccountId());
    TradableInstrument instrument = activeSpotInstrument(command.instrumentId(), cash.currency());
    long lockedVersion = context == null ? transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next()) : 0;
    LedgerAccount investment = investmentAccount(ownerUserId, cash, instrument);
    long grossCostCent = SpotTradeMath.grossCostCent(command.quantity(), command.unitPriceCent());
    Money grossCost = Money.of(grossCostCent, cash.currency());
    Money fee = Money.of(command.feeCent(), cash.currency());
    BalancedPostings postings = LedgerPostingTemplates.spotBuy(cash.accountId(), investment.accountId(), grossCost, fee);
    ensureNonNegativeCash(ownerUserId, cash, postings);
    String transactionId = idGenerator.next();
    String tradeDetailId = idGenerator.next();
    long ledgerVersion = context == null ? transactionPort.reserveNextLedgerVersion(ownerUserId, lockedVersion)
        : context.ledgerVersion();
    LedgerTradeDetail detail = LedgerTradeDetail.spot(tradeDetailId, 1, instrument.instrumentId(),
        PositionEffect.OPEN, command.quantity(), command.unitPriceCent(), command.feeCent());
    LedgerTransaction transaction = transaction(transactionId, ownerUserId, LedgerTransactionType.TRADE_BUY,
        command.occurredOn(), ledgerVersion, command.note(), postings, detail, context, sourceType, importExportFileId);
    transactionPort.append(transaction);
    transactionEventPort.recordAppended(transaction);
    long openedCostCent = exactAdd(grossCostCent, command.feeCent());
    List<FifoLot> lots = lotPort.find(ownerUserId, cash.accountId(), instrument.instrumentId());
    List<FifoLot> updatedLots = new java.util.ArrayList<>(lots);
    updatedLots.add(new FifoLot(tradeDetailId, transactionId, 1, command.occurredOn(),
        Quantity.of(command.quantity()), Quantity.of(command.quantity()), openedCostCent, openedCostCent));
    lotPort.replace(ownerUserId, cash.accountId(), instrument.instrumentId(), cash.currency(), ledgerVersion,
        updatedLots);
    historyReplayer.rebuild(ownerUserId, ledgerVersion);
    return new SpotTradeResult(transaction, grossCostCent, 0, 0);
  }

  @Transactional
  public SpotTradeResult sell(String ownerUserId, SpotTradeCommand command) {
    return sell(ownerUserId, command, null, LedgerSourceType.MANUAL, null);
  }

  /** Appends a sell reconstructed from a confirmed import preview. */
  @Transactional
  public SpotTradeResult sellImported(String ownerUserId, SpotTradeCommand command, String importExportFileId) {
    return sell(ownerUserId, command, null, LedgerSourceType.IMPORT, importExportFileId);
  }

  @Transactional
  public SpotTradeResult sellReplacement(LedgerAppendContext context, SpotTradeCommand command) {
    Objects.requireNonNull(context, "context must not be null");
    return sell(context.ownerUserId(), command, context, LedgerSourceType.CORRECTION_REPLACEMENT, null);
  }

  private SpotTradeResult sell(String ownerUserId, SpotTradeCommand command, LedgerAppendContext context,
      LedgerSourceType sourceType, String importExportFileId) {
    validateCommand(ownerUserId, command);
    LedgerAccount cash = activeCashAccount(ownerUserId, command.cashAccountId());
    TradableInstrument instrument = activeSpotInstrument(command.instrumentId(), cash.currency());
    long lockedVersion = context == null ? transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next()) : 0;
    LedgerAccount investment = investmentAccount(ownerUserId, cash, instrument);
    List<FifoLot> lots = lotPort.find(ownerUserId, cash.accountId(), instrument.instrumentId());
    FifoAllocation allocation = FifoCostAllocator.allocate(lots, command.quantity());
    long grossProceedsCent = SpotTradeMath.grossCostCent(command.quantity(), command.unitPriceCent());
    Money grossProceeds = Money.of(grossProceedsCent, cash.currency());
    Money allocatedCost = Money.of(allocation.allocatedCostCent(), cash.currency());
    Money fee = Money.of(command.feeCent(), cash.currency());
    LedgerAccount feeExpense = activeSystemAccount(ownerUserId, SystemLedgerAccount.FEE_EXPENSE, cash);
    LedgerAccount realizedPnl = activeSystemAccount(ownerUserId, SystemLedgerAccount.REALIZED_PNL, cash);
    BalancedPostings postings = LedgerPostingTemplates.spotSell(cash.accountId(), investment.accountId(),
        feeExpense.accountId(), realizedPnl.accountId(), grossProceeds, allocatedCost, fee);
    ensureNonNegativeCash(ownerUserId, cash, postings);
    String transactionId = idGenerator.next();
    String tradeDetailId = idGenerator.next();
    long ledgerVersion = context == null ? transactionPort.reserveNextLedgerVersion(ownerUserId, lockedVersion)
        : context.ledgerVersion();
    LedgerTradeDetail detail = LedgerTradeDetail.spot(tradeDetailId, 1, instrument.instrumentId(),
        PositionEffect.CLOSE, command.quantity(), command.unitPriceCent(), command.feeCent());
    LedgerTransaction transaction = transaction(transactionId, ownerUserId, LedgerTransactionType.TRADE_SELL,
        command.occurredOn(), ledgerVersion, command.note(), postings, detail, context, sourceType, importExportFileId);
    transactionPort.append(transaction);
    transactionEventPort.recordAppended(transaction);
    lotPort.replace(ownerUserId, cash.accountId(), instrument.instrumentId(), cash.currency(), ledgerVersion,
        allocation.remainingLots());
    historyReplayer.rebuild(ownerUserId, ledgerVersion);
    long netPnl = exactSubtract(exactSubtract(grossProceedsCent, allocation.allocatedCostCent()), command.feeCent());
    return new SpotTradeResult(transaction, grossProceedsCent, allocation.allocatedCostCent(), netPnl);
  }

  private LedgerTransaction transaction(String transactionId, String ownerUserId, LedgerTransactionType type,
      LocalDate occurredOn, long ledgerVersion, String note, BalancedPostings postings, LedgerTradeDetail detail,
      LedgerAppendContext context, LedgerSourceType sourceType, String importExportFileId) {
    List<LedgerPostingFact> facts = postingFacts(postings);
    return context == null
        ? sourceType == LedgerSourceType.IMPORT
            ? LedgerTransaction.imported(transactionId, ownerUserId, type, occurredOn, importExportFileId, ledgerVersion, note, facts,
                List.of(detail))
            : LedgerTransaction.original(transactionId, ownerUserId, type, occurredOn, ledgerVersion, note, facts,
                List.of(detail))
        : LedgerTransaction.replacement(transactionId, ownerUserId, type, occurredOn,
            context.correctionRootTransactionId(), context.revisionNo(), ledgerVersion, note, facts, List.of(detail));
  }

  private LedgerAccount investmentAccount(String ownerUserId, LedgerAccount cash, TradableInstrument instrument) {
    String code = "INV:" + cash.accountId() + ":" + instrument.instrumentId();
    var existing = accountLookupPort.findByOwnerAndCode(ownerUserId, code);
    if (existing.isEmpty()) {
      accountMutationPort.insertSystemIfAbsent(LedgerAccount.newInvestment(idGenerator.next(), ownerUserId,
          cash.accountId(), instrument.instrumentId(), cash.currency()));
      existing = accountLookupPort.findByOwnerAndCode(ownerUserId, code);
    }
    LedgerAccount account = existing.orElseThrow(() -> new IllegalStateException("investment account was not created"));
    if (account.accountKind() != LedgerAccountKind.ASSET_INVESTMENT || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cash.currency()) {
      throw new IllegalStateException("investment account is invalid");
    }
    return account;
  }

  private void ensureNonNegativeCash(String ownerUserId, LedgerAccount cash, BalancedPostings proposed) {
    long balance = 0;
    for (LedgerPostingFact fact : transactionPort.findPostingFactsByOwner(ownerUserId)) {
      if (fact.accountId().equals(cash.accountId())) {
        balance = apply(balance, fact.side(), fact.amount());
      }
    }
    for (Posting posting : proposed.postings()) {
      if (posting.accountId().equals(cash.accountId())) {
        balance = apply(balance, posting.side(), posting.amount());
      }
    }
    if (balance < 0) {
      throw new InsufficientBalanceException(cash.accountId());
    }
  }

  private long apply(long balance, PostingSide side, Money amount) {
    if (amount.currency() == null) {
      throw new IllegalStateException("posting currency is missing");
    }
    try {
      return side == PostingSide.DEBIT ? Math.addExact(balance, amount.cent())
          : Math.subtractExact(balance, amount.cent());
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("cash balance overflow", exception);
    }
  }

  private List<LedgerPostingFact> postingFacts(BalancedPostings postings) {
    int[] postingNo = {1};
    return postings.postings().stream().map(posting -> new LedgerPostingFact(idGenerator.next(), posting.accountId(),
        postingNo[0]++, posting.side(), posting.amount())).toList();
  }

  private LedgerAccount activeCashAccount(String ownerUserId, String accountId) {
    LedgerAccount account = accountLookupPort.findByIdAndOwner(accountId, ownerUserId)
        .orElseThrow(() -> new IllegalArgumentException("cash account was not found for the authenticated owner"));
    if (account.accountKind() != LedgerAccountKind.ASSET_CASH || account.status() != LedgerAccountStatus.ACTIVE) {
      throw new IllegalArgumentException("cash account must be active");
    }
    return account;
  }

  private TradableInstrument activeSpotInstrument(String instrumentId, CurrencyCode cashCurrency) {
    TradableInstrument instrument = instrumentPort.findById(instrumentId)
        .orElseThrow(() -> new IllegalArgumentException("instrument was not found"));
    if (instrument.nativeCurrency() != cashCurrency) {
      throw new IllegalArgumentException("instrument currency must match the selected cash account");
    }
    return instrument;
  }

  private static void validateCommand(String ownerUserId, SpotTradeCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank()) {
      throw new IllegalArgumentException("ownerUserId must not be blank");
    }
    Objects.requireNonNull(command, "command must not be null");
    if (command.cashAccountId() == null || command.cashAccountId().isBlank()
        || command.instrumentId() == null || command.instrumentId().isBlank()
        || command.occurredOn() == null || command.unitPriceCent() <= 0 || command.feeCent() < 0) {
      throw new IllegalArgumentException("spot trade command is invalid");
    }
    Quantity.of(command.quantity());
    if (command.note() != null && command.note().length() > 1_000) {
      throw new IllegalArgumentException("note exceeds 1000 characters");
    }
  }

  private LedgerAccount activeSystemAccount(String ownerUserId, SystemLedgerAccount systemAccount,
      LedgerAccount cash) {
    LedgerAccount account = accountLookupPort.findByOwnerAndCode(ownerUserId, systemAccount.accountCode(cash.currency()))
        .orElseThrow(() -> new IllegalArgumentException("required system account was not provisioned"));
    if (account.accountKind() != systemAccount.accountKind() || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cash.currency()) {
      throw new IllegalArgumentException("required system account is invalid");
    }
    return account;
  }

  private static long exactAdd(long first, long second) {
    try {
      return Math.addExact(first, second);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("money cent overflow", exception);
    }
  }

  private static long exactSubtract(long first, long second) {
    try {
      return Math.subtractExact(first, second);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("money cent overflow", exception);
    }
  }
}
