package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.BalancedPostings;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FuturesLot;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerPostingTemplates;
import com.personal.investment.ledger.domain.LedgerTradeDetail;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.PositionEffect;
import com.personal.investment.ledger.domain.Posting;
import com.personal.investment.ledger.domain.PostingSide;
import com.personal.investment.ledger.domain.PricePrecisionException;
import com.personal.investment.ledger.domain.Quantity;
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit manual daily mark-to-market; no scheduler or market feed can invoke this command. */
@Service
public class FuturesDailySettlementService {
  private final LedgerCommandAccountPort accountLookupPort;
  private final LedgerTransactionPort transactionPort;
  private final FuturesInstrumentPort instrumentPort;
  private final FuturesPositionPort positionPort;
  private final LedgerIdGenerator idGenerator;
  private final LedgerTransactionEventPort transactionEventPort;

  public FuturesDailySettlementService(LedgerCommandAccountPort accountLookupPort, LedgerTransactionPort transactionPort,
      FuturesInstrumentPort instrumentPort, FuturesPositionPort positionPort, LedgerIdGenerator idGenerator,
      LedgerTransactionEventPort transactionEventPort) {
    this.accountLookupPort = accountLookupPort;
    this.transactionPort = transactionPort;
    this.instrumentPort = instrumentPort;
    this.positionPort = positionPort;
    this.idGenerator = idGenerator;
    this.transactionEventPort = transactionEventPort;
  }

  @Transactional
  public FuturesDailySettlementResult settle(String ownerUserId, FuturesDailySettlementCommand command) {
    validateCommand(ownerUserId, command);
    LedgerAccount cash = activeCnyCash(ownerUserId, command.cashAccountId());
    FuturesInstrument instrument = instrumentPort.findById(command.instrumentId())
        .orElseThrow(() -> new IllegalArgumentException("future contract was not found or is incomplete"));
    if (instrument.currency() != CurrencyCode.CNY || command.occurredOn().isAfter(instrument.maturityDate())) {
      throw new IllegalArgumentException("future settlement must use CNY and occur on or before its maturity date");
    }
    long lockedVersion = transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    LedgerAccount available = activeMargin(ownerUserId, "MRGAV:" + cash.accountId(), cash);
    LedgerAccount locked = activeMargin(ownerUserId, "MRGLK:" + cash.accountId(), cash);
    List<FuturesLot> lots = positionPort.find(ownerUserId, locked.accountId(), instrument.instrumentId());
    Settlement settlement = calculate(lots, command, instrument);
    LedgerAccount realizedPnl = activeSystemAccount(ownerUserId, SystemLedgerAccount.REALIZED_PNL, cash);
    List<LedgerPostingFact> postings = settlement.realizedPnlCent() == 0 ? List.of()
        : postingFacts(LedgerPostingTemplates.futuresDailySettlement(available.accountId(), realizedPnl.accountId(),
            settlement.realizedPnlCent(), CurrencyCode.CNY));
    if (!postings.isEmpty()) {
      ensureNonNegative(ownerUserId, postings, available);
    }
    long ledgerVersion = transactionPort.reserveNextLedgerVersion(ownerUserId, lockedVersion);
    String transactionId = idGenerator.next();
    LedgerTradeDetail detail = new LedgerTradeDetail(idGenerator.next(), 1, instrument.instrumentId(), PositionEffect.NONE,
        settlement.openQuantity(), null, command.settlementPricePoints(), instrument.contractMultiplierCent(),
        instrument.maturityDate(), 0, null);
    LedgerTransaction transaction = LedgerTransaction.original(transactionId, ownerUserId,
        LedgerTransactionType.FUTURES_DAILY_SETTLEMENT, command.occurredOn(), ledgerVersion, command.note(), postings,
        List.of(detail));
    transactionPort.append(transaction);
    transactionEventPort.recordAppended(transaction);
    positionPort.replace(ownerUserId, locked.accountId(), instrument.instrumentId(), CurrencyCode.CNY, ledgerVersion,
        settlement.updatedLots());
    return new FuturesDailySettlementResult(transaction, settlement.realizedPnlCent());
  }

  /**
   * Calculates the daily mark-to-market effect without allocating a ledger version or changing any projection.
   * A zero-PnL result intentionally has no monetary posting, but remains a valid settlement preview.
   */
  @Transactional(readOnly = true)
  public FuturesDailySettlementPreviewResult preview(String ownerUserId, FuturesDailySettlementCommand command) {
    validateCommand(ownerUserId, command);
    LedgerAccount cash = activeCnyCash(ownerUserId, command.cashAccountId());
    FuturesInstrument instrument = instrumentPort.findById(command.instrumentId())
        .orElseThrow(() -> new IllegalArgumentException("future contract was not found or is incomplete"));
    if (instrument.currency() != CurrencyCode.CNY || command.occurredOn().isAfter(instrument.maturityDate())) {
      throw new IllegalArgumentException("future settlement must use CNY and occur on or before its maturity date");
    }
    LedgerAccount available = activeMargin(ownerUserId, "MRGAV:" + cash.accountId(), cash);
    LedgerAccount locked = activeMargin(ownerUserId, "MRGLK:" + cash.accountId(), cash);
    Settlement settlement = calculate(positionPort.find(ownerUserId, locked.accountId(), instrument.instrumentId()),
        command, instrument);
    LedgerAccount realizedPnl = activeSystemAccount(ownerUserId, SystemLedgerAccount.REALIZED_PNL, cash);
    List<Posting> postings = settlement.realizedPnlCent() == 0 ? List.of()
        : LedgerPostingTemplates.futuresDailySettlement(available.accountId(), realizedPnl.accountId(),
            settlement.realizedPnlCent(), CurrencyCode.CNY).postings();
    ensurePreviewNonNegative(ownerUserId, postings, available);
    return new FuturesDailySettlementPreviewResult(CurrencyCode.CNY,
        previewPostings(postings, available, realizedPnl), settlement.realizedPnlCent());
  }

  private static void validateCommand(String ownerUserId, FuturesDailySettlementCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank() || command == null || command.cashAccountId() == null
        || command.cashAccountId().isBlank() || command.instrumentId() == null || command.instrumentId().isBlank()
        || command.occurredOn() == null) {
      throw new IllegalArgumentException("futures daily settlement command is invalid");
    }
    Quantity.of(command.settlementPricePoints());
    if (command.note() != null && command.note().length() > 1_000) {
      throw new IllegalArgumentException("note exceeds 1000 characters");
    }
  }

  private static Settlement calculate(List<FuturesLot> lots, FuturesDailySettlementCommand command,
      FuturesInstrument instrument) {
    if (lots.isEmpty()) {
      throw new IllegalArgumentException("future settlement requires an open long position");
    }
    LocalDate latestBaseline = lots.stream().map(FuturesLot::lastSettlementOn).max(LocalDate::compareTo)
        .orElseThrow();
    if (!command.occurredOn().isAfter(latestBaseline)) {
      throw new IllegalArgumentException("future settlement date must be strictly later than its last settlement baseline");
    }
    long totalPnl = 0;
    BigDecimal quantity = BigDecimal.ZERO.setScale(8);
    List<FuturesLot> updated = new ArrayList<>(lots.size());
    for (FuturesLot lot : lots) {
      if (lot.currency() != CurrencyCode.CNY || lot.contractMultiplierCent() != instrument.contractMultiplierCent()) {
        throw new IllegalStateException("futures lot snapshot does not match its immutable contract definition");
      }
      totalPnl = exactAdd(totalPnl, pnlCent(lot, command.settlementPricePoints()));
      quantity = quantity.add(lot.remainingQuantity());
      updated.add(new FuturesLot(lot.sourceTradeDetailId(), lot.openedOn(), lot.openedQuantity(),
          lot.remainingQuantity(), lot.openPricePoints(), command.settlementPricePoints(), command.occurredOn(),
          lot.contractMultiplierCent(), lot.allocatedInitialMarginCent(), lot.remainingInitialMarginCent(),
          lot.currency()));
    }
    return new Settlement(totalPnl, Quantity.of(quantity), List.copyOf(updated));
  }

  private static long pnlCent(FuturesLot lot, BigDecimal settlementPoints) {
    try {
      return settlementPoints.subtract(lot.lastSettlementPricePoints())
          .multiply(BigDecimal.valueOf(lot.contractMultiplierCent())).multiply(lot.remainingQuantity())
          .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    } catch (ArithmeticException exception) {
      throw new PricePrecisionException();
    }
  }

  private LedgerAccount activeCnyCash(String ownerUserId, String accountId) {
    LedgerAccount cash = accountLookupPort.findByIdAndOwner(accountId, ownerUserId)
        .orElseThrow(() -> new IllegalArgumentException("cash account was not found"));
    if (cash.accountKind() != LedgerAccountKind.ASSET_CASH || cash.status() != LedgerAccountStatus.ACTIVE
        || cash.currency() != CurrencyCode.CNY) {
      throw new IllegalArgumentException("future requires an active CNY cash account");
    }
    return cash;
  }

  private LedgerAccount activeMargin(String ownerUserId, String accountCode, LedgerAccount cash) {
    LedgerAccount account = accountLookupPort.findByOwnerAndCode(ownerUserId, accountCode)
        .orElseThrow(() -> new IllegalArgumentException("required futures margin account was not found"));
    if (account.accountKind() != LedgerAccountKind.ASSET_MARGIN || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cash.currency() || !account.accountCode().equals(accountCode)) {
      throw new IllegalStateException("futures margin account is invalid");
    }
    return account;
  }

  private LedgerAccount activeSystemAccount(String ownerUserId, SystemLedgerAccount systemAccount,
      LedgerAccount cash) {
    LedgerAccount account = accountLookupPort.findByOwnerAndCode(ownerUserId, systemAccount.accountCode(cash.currency()))
        .orElseThrow(() -> new IllegalStateException("required system account was not provisioned"));
    if (account.accountKind() != systemAccount.accountKind() || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cash.currency()) {
      throw new IllegalStateException("required system account is invalid");
    }
    return account;
  }

  private void ensureNonNegative(String ownerUserId, List<LedgerPostingFact> proposed, LedgerAccount account) {
    long balance = 0;
    for (LedgerPostingFact posting : transactionPort.findPostingFactsByOwner(ownerUserId)) {
      if (posting.accountId().equals(account.accountId())) {
        balance = apply(balance, posting.side(), posting.amount());
      }
    }
    for (LedgerPostingFact posting : proposed) {
      if (posting.accountId().equals(account.accountId())) {
        balance = apply(balance, posting.side(), posting.amount());
      }
    }
    if (balance < 0) {
      throw new InsufficientBalanceException(account.accountId());
    }
  }

  private void ensurePreviewNonNegative(String ownerUserId, List<Posting> proposed, LedgerAccount account) {
    long balance = 0;
    for (LedgerPostingFact posting : transactionPort.findPostingFactsByOwner(ownerUserId)) {
      if (posting.accountId().equals(account.accountId())) {
        balance = apply(balance, posting.side(), posting.amount());
      }
    }
    for (Posting posting : proposed) {
      if (posting.accountId().equals(account.accountId())) {
        balance = apply(balance, posting.side(), posting.amount());
      }
    }
    if (balance < 0) {
      throw new InsufficientBalanceException(account.accountId());
    }
  }

  private static long apply(long balance, PostingSide side, Money amount) {
    try {
      return side == PostingSide.DEBIT ? Math.addExact(balance, amount.cent())
          : Math.subtractExact(balance, amount.cent());
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("future balance overflow", exception);
    }
  }

  private List<LedgerPostingFact> postingFacts(BalancedPostings postings) {
    int[] no = {1};
    return postings.postings().stream().map(posting -> new LedgerPostingFact(idGenerator.next(), posting.accountId(),
        no[0]++, posting.side(), posting.amount())).toList();
  }

  private static List<PreviewPosting> previewPostings(List<Posting> postings, LedgerAccount... accounts) {
    Map<String, LedgerAccount> byId = new HashMap<>();
    for (LedgerAccount account : accounts) {
      byId.put(account.accountId(), account);
    }
    return postings.stream().map(posting -> {
      LedgerAccount account = byId.get(posting.accountId());
      if (account == null) {
        throw new IllegalStateException("future settlement preview account metadata is missing");
      }
      return new PreviewPosting(account.accountCode(), account.displayName(), posting.side(), posting.amount().cent(),
          posting.amount().currency());
    }).toList();
  }

  private static long exactAdd(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      throw new PricePrecisionException();
    }
  }

  private record Settlement(long realizedPnlCent, BigDecimal openQuantity, List<FuturesLot> updatedLots) {
  }
}
