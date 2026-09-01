package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.BalancedPostings;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FuturesLot;
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
import com.personal.investment.ledger.domain.PricePrecisionException;
import com.personal.investment.ledger.domain.Quantity;
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Closes CFFEX long futures FIFO, releases exact locked margin and settles PnL from each lot baseline. */
@Service
public class FuturesCloseService {
  private static final Comparator<FuturesLot> FIFO_ORDER = Comparator.comparing(FuturesLot::openedOn)
      .thenComparing(FuturesLot::sourceTradeDetailId);

  private final LedgerCommandAccountPort accountLookupPort;
  private final LedgerTransactionPort transactionPort;
  private final FuturesInstrumentPort instrumentPort;
  private final FuturesPositionPort positionPort;
  private final LedgerIdGenerator idGenerator;
  private final LedgerTransactionEventPort transactionEventPort;

  public FuturesCloseService(LedgerCommandAccountPort accountLookupPort, LedgerTransactionPort transactionPort,
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
  public FuturesCloseResult close(String ownerUserId, FuturesCloseCommand command) {
    return close(ownerUserId, command, LedgerSourceType.MANUAL, null);
  }

  /** Appends a futures close reconstructed from a confirmed import preview. */
  @Transactional
  public FuturesCloseResult closeImported(String ownerUserId, FuturesCloseCommand command, String importExportFileId) {
    return close(ownerUserId, command, LedgerSourceType.IMPORT, importExportFileId);
  }

  private FuturesCloseResult close(String ownerUserId, FuturesCloseCommand command, LedgerSourceType sourceType,
      String importExportFileId) {
    validateCommand(ownerUserId, command);
    LedgerAccount cash = activeCnyCash(ownerUserId, command.cashAccountId());
    FuturesInstrument instrument = instrumentPort.findById(command.instrumentId())
        .orElseThrow(() -> new IllegalArgumentException("future contract was not found or is incomplete"));
    if (instrument.currency() != CurrencyCode.CNY || command.occurredOn().isAfter(instrument.maturityDate())) {
      throw new IllegalArgumentException("future close must use CNY and occur on or before its maturity date");
    }
    long lockedVersion = transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    LedgerAccount available = activeMargin(ownerUserId, "MRGAV:" + cash.accountId(), cash);
    LedgerAccount locked = activeMargin(ownerUserId, "MRGLK:" + cash.accountId(), cash);
    List<FuturesLot> lots = positionPort.find(ownerUserId, locked.accountId(), instrument.instrumentId());
    FuturesCloseAllocation allocation = allocate(lots, command.quantity(), command.pricePoints(), instrument);
    LedgerAccount feeExpense = activeSystemAccount(ownerUserId, SystemLedgerAccount.FEE_EXPENSE, cash);
    LedgerAccount realizedPnl = activeSystemAccount(ownerUserId, SystemLedgerAccount.REALIZED_PNL, cash);
    BalancedPostings postings = LedgerPostingTemplates.futuresClose(available.accountId(), locked.accountId(),
        cash.accountId(), feeExpense.accountId(), realizedPnl.accountId(),
        Money.of(allocation.releasedMarginCent(), CurrencyCode.CNY), allocation.realizedPnlCent(),
        Money.of(command.feeCent(), CurrencyCode.CNY));
    ensureNonNegative(ownerUserId, postings, cash, available, locked);
    long ledgerVersion = transactionPort.reserveNextLedgerVersion(ownerUserId, lockedVersion);
    String transactionId = idGenerator.next();
    String detailId = idGenerator.next();
    LedgerTradeDetail detail = new LedgerTradeDetail(detailId, 1, instrument.instrumentId(), PositionEffect.CLOSE,
        command.quantity(), null, command.pricePoints(), instrument.contractMultiplierCent(), instrument.maturityDate(),
        command.feeCent(), null);
    LedgerTransaction transaction = sourceType == LedgerSourceType.IMPORT
        ? LedgerTransaction.imported(transactionId, ownerUserId, LedgerTransactionType.FUTURES_CLOSE,
            command.occurredOn(), importExportFileId, ledgerVersion, command.note(), postingFacts(postings), List.of(detail))
        : LedgerTransaction.original(transactionId, ownerUserId, LedgerTransactionType.FUTURES_CLOSE,
            command.occurredOn(), ledgerVersion, command.note(), postingFacts(postings), List.of(detail));
    transactionPort.append(transaction);
    transactionEventPort.recordAppended(transaction);
    positionPort.replace(ownerUserId, locked.accountId(), instrument.instrumentId(), CurrencyCode.CNY, ledgerVersion,
        allocation.remainingLots());
    return new FuturesCloseResult(transaction, allocation.releasedMarginCent(), allocation.realizedPnlCent());
  }

  @Transactional(readOnly = true)
  public FuturesClosePreviewResult preview(String ownerUserId, FuturesCloseCommand command) {
    validateCommand(ownerUserId, command);
    LedgerAccount cash = activeCnyCash(ownerUserId, command.cashAccountId());
    FuturesInstrument instrument = instrumentPort.findById(command.instrumentId())
        .orElseThrow(() -> new IllegalArgumentException("future contract was not found or is incomplete"));
    if (instrument.currency() != CurrencyCode.CNY || command.occurredOn().isAfter(instrument.maturityDate())) {
      throw new IllegalArgumentException("future close must use CNY and occur on or before its maturity date");
    }
    LedgerAccount available = activeMargin(ownerUserId, "MRGAV:" + cash.accountId(), cash);
    LedgerAccount locked = activeMargin(ownerUserId, "MRGLK:" + cash.accountId(), cash);
    FuturesCloseAllocation allocation = allocate(positionPort.find(ownerUserId, locked.accountId(), instrument.instrumentId()),
        command.quantity(), command.pricePoints(), instrument);
    LedgerAccount feeExpense = activeSystemAccount(ownerUserId, SystemLedgerAccount.FEE_EXPENSE, cash);
    LedgerAccount realizedPnl = activeSystemAccount(ownerUserId, SystemLedgerAccount.REALIZED_PNL, cash);
    BalancedPostings postings = LedgerPostingTemplates.futuresClose(available.accountId(), locked.accountId(),
        cash.accountId(), feeExpense.accountId(), realizedPnl.accountId(),
        Money.of(allocation.releasedMarginCent(), CurrencyCode.CNY), allocation.realizedPnlCent(),
        Money.of(command.feeCent(), CurrencyCode.CNY));
    ensureNonNegative(ownerUserId, postings, cash, available, locked);
    return new FuturesClosePreviewResult(CurrencyCode.CNY, previewPostings(postings, cash, available, locked,
        feeExpense, realizedPnl), allocation.releasedMarginCent(), allocation.realizedPnlCent());
  }

  private static void validateCommand(String ownerUserId, FuturesCloseCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank() || command == null || command.cashAccountId() == null
        || command.cashAccountId().isBlank() || command.instrumentId() == null || command.instrumentId().isBlank()
        || command.occurredOn() == null || command.feeCent() < 0) {
      throw new IllegalArgumentException("futures close command is invalid");
    }
    Quantity.of(command.quantity());
    Quantity.of(command.pricePoints());
    if (command.quantity().stripTrailingZeros().scale() > 0) {
      throw new IllegalArgumentException("futures quantity must be a positive whole lot count");
    }
    if (command.note() != null && command.note().length() > 1_000) {
      throw new IllegalArgumentException("note exceeds 1000 characters");
    }
  }

  private FuturesCloseAllocation allocate(List<FuturesLot> lots, BigDecimal quantity, BigDecimal closePricePoints,
      FuturesInstrument instrument) {
    BigDecimal remainingToClose = Quantity.of(quantity);
    List<FuturesLot> remainingLots = new ArrayList<>();
    long totalReleasedMargin = 0;
    long totalPnl = 0;
    for (FuturesLot lot : lots.stream().sorted(FIFO_ORDER).toList()) {
      if (remainingToClose.signum() == 0) {
        remainingLots.add(lot);
        continue;
      }
      validateLotSnapshot(lot, instrument);
      BigDecimal consumed = remainingToClose.min(lot.remainingQuantity());
      long releasedMargin = allocateMargin(lot, consumed);
      long pnl = pnlCent(lot, consumed, closePricePoints);
      totalReleasedMargin = exactAdd(totalReleasedMargin, releasedMargin);
      totalPnl = exactAdd(totalPnl, pnl);
      BigDecimal afterQuantity = lot.remainingQuantity().subtract(consumed);
      long afterMargin = exactSubtract(lot.remainingInitialMarginCent(), releasedMargin, "remaining margin underflow");
      if (afterQuantity.signum() > 0) {
        remainingLots.add(new FuturesLot(lot.sourceTradeDetailId(), lot.openedOn(), lot.openedQuantity(), afterQuantity,
            lot.openPricePoints(), lot.lastSettlementPricePoints(), lot.lastSettlementOn(), lot.contractMultiplierCent(),
            lot.allocatedInitialMarginCent(), afterMargin, lot.currency()));
      } else if (afterMargin != 0) {
        throw new IllegalStateException("final futures close must release all remaining margin");
      }
      remainingToClose = remainingToClose.subtract(consumed);
    }
    if (remainingToClose.signum() != 0) {
      throw new InsufficientPositionException();
    }
    if (totalReleasedMargin <= 0) {
      throw new IllegalStateException("futures close did not release a representable margin amount");
    }
    return new FuturesCloseAllocation(totalReleasedMargin, totalPnl, List.copyOf(remainingLots));
  }

  private static void validateLotSnapshot(FuturesLot lot, FuturesInstrument instrument) {
    if (lot.currency() != CurrencyCode.CNY || lot.contractMultiplierCent() != instrument.contractMultiplierCent()) {
      throw new IllegalStateException("futures lot snapshot does not match its immutable contract definition");
    }
  }

  private static long allocateMargin(FuturesLot lot, BigDecimal consumed) {
    if (consumed.compareTo(lot.remainingQuantity()) == 0) {
      return lot.remainingInitialMarginCent();
    }
    try {
      return BigDecimal.valueOf(lot.remainingInitialMarginCent()).multiply(consumed)
          .divide(lot.remainingQuantity(), 0, RoundingMode.DOWN).longValueExact();
    } catch (ArithmeticException exception) {
      throw new PricePrecisionException();
    }
  }

  private static long pnlCent(FuturesLot lot, BigDecimal quantity, BigDecimal closePricePoints) {
    try {
      return closePricePoints.subtract(lot.lastSettlementPricePoints())
          .multiply(BigDecimal.valueOf(lot.contractMultiplierCent())).multiply(quantity)
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
    LedgerAccount margin = accountLookupPort.findByOwnerAndCode(ownerUserId, accountCode)
        .orElseThrow(() -> new IllegalArgumentException("required futures margin account was not found"));
    if (margin.accountKind() != LedgerAccountKind.ASSET_MARGIN || margin.status() != LedgerAccountStatus.ACTIVE
        || margin.currency() != cash.currency() || !margin.accountCode().equals(accountCode)) {
      throw new IllegalStateException("futures margin account is invalid");
    }
    return margin;
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

  private void ensureNonNegative(String ownerUserId, BalancedPostings proposed, LedgerAccount... accounts) {
    Map<String, LedgerAccount> byId = new HashMap<>();
    Map<String, Long> balances = new HashMap<>();
    for (LedgerAccount account : accounts) {
      byId.put(account.accountId(), account);
      balances.put(account.accountId(), 0L);
    }
    for (LedgerPostingFact posting : transactionPort.findPostingFactsByOwner(ownerUserId)) {
      apply(byId, balances, posting.accountId(), posting.side(), posting.amount());
    }
    for (Posting posting : proposed.postings()) {
      apply(byId, balances, posting.accountId(), posting.side(), posting.amount());
    }
    balances.forEach((accountId, balance) -> {
      if (balance < 0) {
        throw new InsufficientBalanceException(accountId);
      }
    });
  }

  private static void apply(Map<String, LedgerAccount> accounts, Map<String, Long> balances, String accountId,
      PostingSide side, Money amount) {
    LedgerAccount account = accounts.get(accountId);
    if (account == null) {
      return;
    }
    if (account.currency() != amount.currency()) {
      throw new IllegalStateException("future posting currency does not match account currency");
    }
    try {
      balances.put(accountId, side == PostingSide.DEBIT
          ? Math.addExact(balances.get(accountId), amount.cent())
          : Math.subtractExact(balances.get(accountId), amount.cent()));
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("future balance overflow", exception);
    }
  }

  private List<LedgerPostingFact> postingFacts(BalancedPostings postings) {
    int[] no = {1};
    return postings.postings().stream().map(posting -> new LedgerPostingFact(idGenerator.next(), posting.accountId(),
        no[0]++, posting.side(), posting.amount())).toList();
  }

  private static List<PreviewPosting> previewPostings(BalancedPostings postings, LedgerAccount... accounts) {
    Map<String, LedgerAccount> byId = new HashMap<>();
    for (LedgerAccount account : accounts) {
      byId.put(account.accountId(), account);
    }
    return postings.postings().stream().map(posting -> {
      LedgerAccount account = byId.get(posting.accountId());
      if (account == null) {
        throw new IllegalStateException("future close preview account metadata is missing");
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

  private static long exactSubtract(long left, long right, String message) {
    try {
      return Math.subtractExact(left, right);
    } catch (ArithmeticException exception) {
      throw new IllegalStateException(message, exception);
    }
  }

  private record FuturesCloseAllocation(long releasedMarginCent, long realizedPnlCent, List<FuturesLot> remainingLots) {
  }
}
