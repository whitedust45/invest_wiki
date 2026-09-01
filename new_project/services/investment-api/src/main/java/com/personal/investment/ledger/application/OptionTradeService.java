package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.BalancedPostings;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FifoAllocation;
import com.personal.investment.ledger.domain.FifoCostAllocator;
import com.personal.investment.ledger.domain.FifoLot;
import com.personal.investment.ledger.domain.InsufficientPositionException;
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
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commands for long options only: premium open, FIFO close, and explicitly confirmed worthless expiry. */
@Service
public class OptionTradeService {
  private final LedgerCommandAccountPort accountLookupPort;
  private final LedgerAccountPort accountMutationPort;
  private final LedgerTransactionPort transactionPort;
  private final OptionInstrumentPort instrumentPort;
  private final SpotLotPort lotPort;
  private final LedgerIdGenerator idGenerator;
  private final LedgerTransactionEventPort transactionEventPort;

  public OptionTradeService(LedgerCommandAccountPort accountLookupPort, LedgerAccountPort accountMutationPort,
      LedgerTransactionPort transactionPort, OptionInstrumentPort instrumentPort, SpotLotPort lotPort,
      LedgerIdGenerator idGenerator, LedgerTransactionEventPort transactionEventPort) {
    this.accountLookupPort = accountLookupPort;
    this.accountMutationPort = accountMutationPort;
    this.transactionPort = transactionPort;
    this.instrumentPort = instrumentPort;
    this.lotPort = lotPort;
    this.idGenerator = idGenerator;
    this.transactionEventPort = transactionEventPort;
  }

  @Transactional
  public OptionTradeResult open(String ownerUserId, OptionTradeCommand command) {
    return open(ownerUserId, command, LedgerSourceType.MANUAL, null);
  }

  /** Appends an option open reconstructed from a confirmed import preview. */
  @Transactional
  public OptionTradeResult openImported(String ownerUserId, OptionTradeCommand command, String importExportFileId) {
    return open(ownerUserId, command, LedgerSourceType.IMPORT, importExportFileId);
  }

  private OptionTradeResult open(String ownerUserId, OptionTradeCommand command, LedgerSourceType sourceType,
      String importExportFileId) {
    validateTrade(ownerUserId, command);
    LedgerAccount cash = activeCash(ownerUserId, command.cashAccountId());
    OptionInstrument option = activeOption(command.instrumentId(), cash.currency(), command.occurredOn(), false);
    long lockedVersion = transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    LedgerAccount investment = investmentAccount(ownerUserId, cash, option.instrumentId());
    long premiumCent = premiumCent(command.quantity(), option.contractMultiplier(), command.unitPriceCent());
    BalancedPostings postings = LedgerPostingTemplates.optionOpen(cash.accountId(), investment.accountId(),
        Money.of(premiumCent, cash.currency()), Money.of(command.feeCent(), cash.currency()));
    ensureNonNegativeCash(ownerUserId, cash, postings);
    long ledgerVersion = transactionPort.reserveNextLedgerVersion(ownerUserId, lockedVersion);
    String transactionId = idGenerator.next();
    String detailId = idGenerator.next();
    LedgerTradeDetail detail = new LedgerTradeDetail(detailId, 1, option.instrumentId(), PositionEffect.OPEN,
        command.quantity(), command.unitPriceCent(), null, null, option.maturityDate(), command.feeCent(),
        option.contractMultiplier());
    LedgerTransaction transaction = append(transactionId, ownerUserId, LedgerTransactionType.OPTION_OPEN,
        command.occurredOn(), ledgerVersion, command.note(), postings, detail, sourceType, importExportFileId);
    long capitalizedCost = exactAdd(premiumCent, command.feeCent());
    List<FifoLot> lots = lotPort.find(ownerUserId, cash.accountId(), option.instrumentId());
    List<FifoLot> updated = new java.util.ArrayList<>(lots);
    updated.add(new FifoLot(detailId, transactionId, 1, command.occurredOn(), command.quantity(), command.quantity(),
        capitalizedCost, capitalizedCost));
    lotPort.replace(ownerUserId, cash.accountId(), option.instrumentId(), cash.currency(), ledgerVersion, updated);
    return new OptionTradeResult(transaction, premiumCent, 0, 0);
  }

  @Transactional
  public OptionTradeResult close(String ownerUserId, OptionTradeCommand command) {
    return close(ownerUserId, command, LedgerSourceType.MANUAL, null);
  }

  /** Appends an option close reconstructed from a confirmed import preview. */
  @Transactional
  public OptionTradeResult closeImported(String ownerUserId, OptionTradeCommand command, String importExportFileId) {
    return close(ownerUserId, command, LedgerSourceType.IMPORT, importExportFileId);
  }

  private OptionTradeResult close(String ownerUserId, OptionTradeCommand command, LedgerSourceType sourceType,
      String importExportFileId) {
    validateTrade(ownerUserId, command);
    LedgerAccount cash = activeCash(ownerUserId, command.cashAccountId());
    OptionInstrument option = activeOption(command.instrumentId(), cash.currency(), command.occurredOn(), false);
    long lockedVersion = transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    LedgerAccount investment = existingInvestmentAccount(ownerUserId, cash, option.instrumentId());
    FifoAllocation allocation = FifoCostAllocator.allocate(lotPort.find(ownerUserId, cash.accountId(),
        option.instrumentId()), command.quantity());
    long premiumCent = premiumCent(command.quantity(), option.contractMultiplier(), command.unitPriceCent());
    LedgerAccount feeExpense = activeSystem(ownerUserId, SystemLedgerAccount.FEE_EXPENSE, cash);
    LedgerAccount realizedPnl = activeSystem(ownerUserId, SystemLedgerAccount.REALIZED_PNL, cash);
    BalancedPostings postings = LedgerPostingTemplates.optionClose(cash.accountId(), investment.accountId(),
        feeExpense.accountId(), realizedPnl.accountId(), Money.of(premiumCent, cash.currency()),
        Money.of(allocation.allocatedCostCent(), cash.currency()), Money.of(command.feeCent(), cash.currency()));
    ensureNonNegativeCash(ownerUserId, cash, postings);
    long ledgerVersion = transactionPort.reserveNextLedgerVersion(ownerUserId, lockedVersion);
    String transactionId = idGenerator.next();
    String detailId = idGenerator.next();
    LedgerTradeDetail detail = new LedgerTradeDetail(detailId, 1, option.instrumentId(), PositionEffect.CLOSE,
        command.quantity(), command.unitPriceCent(), null, null, option.maturityDate(), command.feeCent(),
        option.contractMultiplier());
    LedgerTransaction transaction = append(transactionId, ownerUserId, LedgerTransactionType.OPTION_CLOSE,
        command.occurredOn(), ledgerVersion, command.note(), postings, detail, sourceType, importExportFileId);
    lotPort.replace(ownerUserId, cash.accountId(), option.instrumentId(), cash.currency(), ledgerVersion,
        allocation.remainingLots());
    return new OptionTradeResult(transaction, premiumCent, allocation.allocatedCostCent(),
        exactSubtract(exactSubtract(premiumCent, allocation.allocatedCostCent()), command.feeCent()));
  }

  @Transactional
  public OptionTradeResult expire(String ownerUserId, OptionExpiryCommand command) {
    return expire(ownerUserId, command, LedgerSourceType.MANUAL, null);
  }

  /** Appends a confirmed-worthless option expiry reconstructed from a historical import. */
  @Transactional
  public OptionTradeResult expireImported(String ownerUserId, OptionExpiryCommand command, String importExportFileId) {
    return expire(ownerUserId, command, LedgerSourceType.IMPORT, importExportFileId);
  }

  /** Legacy expiry rows contain no reliable quantity; a confirmed WORTHLESS row closes the entire then-open lot set. */
  @Transactional
  public OptionTradeResult expireAllImported(String ownerUserId, String cashAccountId, String instrumentId,
      LocalDate occurredOn, String note, String importExportFileId) {
    LedgerAccount cash = activeCash(ownerUserId, cashAccountId);
    BigDecimal quantity = lotPort.find(ownerUserId, cash.accountId(), instrumentId).stream()
        .map(FifoLot::remainingQuantity).reduce(BigDecimal.ZERO.setScale(8), BigDecimal::add);
    if (quantity.signum() <= 0) {
      throw new InsufficientPositionException();
    }
    return expireImported(ownerUserId, new OptionExpiryCommand(cashAccountId, instrumentId, occurredOn, quantity,
        OptionExpiryOutcome.WORTHLESS, note), importExportFileId);
  }

  private OptionTradeResult expire(String ownerUserId, OptionExpiryCommand command, LedgerSourceType sourceType,
      String importExportFileId) {
    validateExpiry(ownerUserId, command);
    LedgerAccount cash = activeCash(ownerUserId, command.cashAccountId());
    OptionInstrument option = activeOption(command.instrumentId(), cash.currency(), command.occurredOn(), true);
    long lockedVersion = transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    LedgerAccount investment = existingInvestmentAccount(ownerUserId, cash, option.instrumentId());
    List<FifoLot> lots = lotPort.find(ownerUserId, cash.accountId(), option.instrumentId());
    BigDecimal totalQuantity = lots.stream().map(FifoLot::remainingQuantity)
        .reduce(BigDecimal.ZERO.setScale(8), BigDecimal::add);
    if (totalQuantity.signum() <= 0) {
      throw new InsufficientPositionException();
    }
    if (command.quantity().compareTo(totalQuantity) != 0) {
      throw new IllegalArgumentException("option expiry quantity must cover the entire open position");
    }
    FifoAllocation allocation = FifoCostAllocator.allocate(lots, command.quantity());
    LedgerAccount optionExpense = activeSystem(ownerUserId, SystemLedgerAccount.OPTION_EXPENSE, cash);
    BalancedPostings postings = LedgerPostingTemplates.optionExpire(investment.accountId(), optionExpense.accountId(),
        Money.of(allocation.allocatedCostCent(), cash.currency()));
    long ledgerVersion = transactionPort.reserveNextLedgerVersion(ownerUserId, lockedVersion);
    String transactionId = idGenerator.next();
    LedgerTradeDetail detail = LedgerTradeDetail.optionExpiry(idGenerator.next(), 1, option.instrumentId(),
        command.quantity(), option.contractMultiplier());
    LedgerTransaction transaction = append(transactionId, ownerUserId, LedgerTransactionType.OPTION_EXPIRE,
        command.occurredOn(), ledgerVersion, command.note(), postings, detail, sourceType, importExportFileId);
    lotPort.replace(ownerUserId, cash.accountId(), option.instrumentId(), cash.currency(), ledgerVersion,
        allocation.remainingLots());
    return new OptionTradeResult(transaction, 0, allocation.allocatedCostCent(), 0);
  }

  private LedgerTransaction append(String transactionId, String ownerUserId, LedgerTransactionType type,
      LocalDate occurredOn, long ledgerVersion, String note, BalancedPostings postings, LedgerTradeDetail detail,
      LedgerSourceType sourceType, String importExportFileId) {
    int[] no = {1};
    List<LedgerPostingFact> facts = postings.postings().stream().map(posting -> new LedgerPostingFact(idGenerator.next(),
        posting.accountId(), no[0]++, posting.side(), posting.amount())).toList();
    LedgerTransaction transaction = sourceType == LedgerSourceType.IMPORT
        ? LedgerTransaction.imported(transactionId, ownerUserId, type, occurredOn, importExportFileId, ledgerVersion, note, facts,
            List.of(detail))
        : LedgerTransaction.original(transactionId, ownerUserId, type, occurredOn, ledgerVersion, note, facts,
            List.of(detail));
    transactionPort.append(transaction);
    transactionEventPort.recordAppended(transaction);
    return transaction;
  }

  private LedgerAccount activeCash(String ownerUserId, String accountId) {
    LedgerAccount cash = accountLookupPort.findByIdAndOwner(accountId, ownerUserId)
        .orElseThrow(() -> new IllegalArgumentException("cash account was not found"));
    if (cash.accountKind() != LedgerAccountKind.ASSET_CASH || cash.status() != LedgerAccountStatus.ACTIVE) {
      throw new IllegalArgumentException("cash account must be active");
    }
    return cash;
  }

  private OptionInstrument activeOption(String instrumentId, CurrencyCode currency, LocalDate occurredOn,
      boolean requiresMaturityDate) {
    OptionInstrument option = instrumentPort.findById(instrumentId)
        .orElseThrow(() -> new IllegalArgumentException("option contract was not found or is incomplete"));
    if (option.currency() != currency) {
      throw new IllegalArgumentException("option currency must match the selected cash account");
    }
    if (requiresMaturityDate ? !occurredOn.equals(option.maturityDate()) : occurredOn.isAfter(option.maturityDate())) {
      throw new IllegalArgumentException(requiresMaturityDate
          ? "option expiry must occur on the maturity date" : "option open or close must occur on or before maturity");
    }
    return option;
  }

  private LedgerAccount investmentAccount(String ownerUserId, LedgerAccount cash, String instrumentId) {
    String code = investmentCode(cash.accountId(), instrumentId);
    LedgerAccount existing = accountLookupPort.findByOwnerAndCode(ownerUserId, code).orElse(null);
    if (existing == null) {
      accountMutationPort.insertSystemIfAbsent(LedgerAccount.newInvestment(idGenerator.next(), ownerUserId,
          cash.accountId(), instrumentId, cash.currency()));
      existing = accountLookupPort.findByOwnerAndCode(ownerUserId, code)
          .orElseThrow(() -> new IllegalStateException("option investment account was not created"));
    }
    return validInvestment(existing, cash);
  }

  private LedgerAccount existingInvestmentAccount(String ownerUserId, LedgerAccount cash, String instrumentId) {
    return validInvestment(accountLookupPort.findByOwnerAndCode(ownerUserId, investmentCode(cash.accountId(), instrumentId))
        .orElseThrow(() -> new IllegalArgumentException("option investment account was not found")), cash);
  }

  private static LedgerAccount validInvestment(LedgerAccount account, LedgerAccount cash) {
    if (account.accountKind() != LedgerAccountKind.ASSET_INVESTMENT || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cash.currency()) {
      throw new IllegalStateException("option investment account is invalid");
    }
    return account;
  }

  private LedgerAccount activeSystem(String ownerUserId, SystemLedgerAccount systemAccount, LedgerAccount cash) {
    LedgerAccount account = accountLookupPort.findByOwnerAndCode(ownerUserId, systemAccount.accountCode(cash.currency()))
        .orElseThrow(() -> new IllegalStateException("required system account was not provisioned"));
    if (account.accountKind() != systemAccount.accountKind() || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cash.currency()) {
      throw new IllegalStateException("required system account is invalid");
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

  private static long premiumCent(BigDecimal quantity, long multiplier, long unitPriceCent) {
    try {
      return quantity.multiply(BigDecimal.valueOf(multiplier)).multiply(BigDecimal.valueOf(unitPriceCent))
          .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("option premium is not representable in whole minor units", exception);
    }
  }

  private static void validateTrade(String ownerUserId, OptionTradeCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank() || command == null || command.cashAccountId() == null
        || command.cashAccountId().isBlank() || command.instrumentId() == null || command.instrumentId().isBlank()
        || command.occurredOn() == null || command.unitPriceCent() <= 0 || command.feeCent() < 0) {
      throw new IllegalArgumentException("option trade command is invalid");
    }
    Quantity.of(command.quantity());
    if (command.note() != null && command.note().length() > 1_000) {
      throw new IllegalArgumentException("note exceeds 1000 characters");
    }
  }

  private static void validateExpiry(String ownerUserId, OptionExpiryCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank() || command == null || command.cashAccountId() == null
        || command.cashAccountId().isBlank() || command.instrumentId() == null || command.instrumentId().isBlank()
        || command.occurredOn() == null || command.expiryOutcome() != OptionExpiryOutcome.WORTHLESS) {
      throw new IllegalArgumentException("option expiry command requires explicit WORTHLESS confirmation");
    }
    Quantity.of(command.quantity());
    if (command.note() != null && command.note().length() > 1_000) {
      throw new IllegalArgumentException("note exceeds 1000 characters");
    }
  }

  private static long apply(long balance, PostingSide side, Money amount) {
    try {
      return side == PostingSide.DEBIT ? Math.addExact(balance, amount.cent())
          : Math.subtractExact(balance, amount.cent());
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("cash balance overflow", exception);
    }
  }

  private static String investmentCode(String cashAccountId, String instrumentId) {
    return "INV:" + cashAccountId + ":" + instrumentId;
  }

  private static long exactAdd(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("money cent overflow", exception);
    }
  }

  private static long exactSubtract(long left, long right) {
    try {
      return Math.subtractExact(left, right);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("money cent overflow", exception);
    }
  }
}
