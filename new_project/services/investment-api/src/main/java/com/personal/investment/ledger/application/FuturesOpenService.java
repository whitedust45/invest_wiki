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
import com.personal.investment.ledger.domain.LedgerSourceType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.PositionEffect;
import com.personal.investment.ledger.domain.Posting;
import com.personal.investment.ledger.domain.PostingSide;
import com.personal.investment.ledger.domain.Quantity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opens CFFEX long futures only after available margin has been explicitly funded. */
@Service
public class FuturesOpenService {
  private final LedgerCommandAccountPort accountLookupPort;
  private final LedgerAccountPort accountMutationPort;
  private final LedgerTransactionPort transactionPort;
  private final FuturesInstrumentPort instrumentPort;
  private final FuturesPositionPort positionPort;
  private final LedgerIdGenerator idGenerator;
  private final LedgerTransactionEventPort transactionEventPort;

  public FuturesOpenService(LedgerCommandAccountPort accountLookupPort, LedgerAccountPort accountMutationPort,
      LedgerTransactionPort transactionPort, FuturesInstrumentPort instrumentPort, FuturesPositionPort positionPort,
      LedgerIdGenerator idGenerator, LedgerTransactionEventPort transactionEventPort) {
    this.accountLookupPort = accountLookupPort;
    this.accountMutationPort = accountMutationPort;
    this.transactionPort = transactionPort;
    this.instrumentPort = instrumentPort;
    this.positionPort = positionPort;
    this.idGenerator = idGenerator;
    this.transactionEventPort = transactionEventPort;
  }

  @Transactional
  public FuturesOpenResult open(String ownerUserId, FuturesOpenCommand command) {
    return open(ownerUserId, command, LedgerSourceType.MANUAL, null);
  }

  /** Appends a futures open reconstructed from a confirmed import preview. */
  @Transactional
  public FuturesOpenResult openImported(String ownerUserId, FuturesOpenCommand command, String importExportFileId) {
    return open(ownerUserId, command, LedgerSourceType.IMPORT, importExportFileId);
  }

  private FuturesOpenResult open(String ownerUserId, FuturesOpenCommand command, LedgerSourceType sourceType,
      String importExportFileId) {
    validateCommand(ownerUserId, command);
    LedgerAccount cash = activeCnyCash(ownerUserId, command.cashAccountId());
    FuturesInstrument instrument = instrumentPort.findById(command.instrumentId())
        .orElseThrow(() -> new IllegalArgumentException("future contract was not found or is incomplete"));
    if (instrument.currency() != CurrencyCode.CNY || !command.occurredOn().isBefore(instrument.maturityDate())) {
      throw new IllegalArgumentException("future must use CNY and open before its maturity date");
    }
    long lockedVersion = transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    MarginAccounts margins = resolveMarginAccounts(ownerUserId, cash, true);
    LedgerAccount feeExpense = activeSystemAccount(ownerUserId, cash, "SYS:FEE_EXPENSE:CNY",
        LedgerAccountKind.EXPENSE_FEE);
    Money initialMargin = Money.of(command.initialMarginCent(), CurrencyCode.CNY);
    Money fee = Money.of(command.feeCent(), CurrencyCode.CNY);
    BalancedPostings postings = LedgerPostingTemplates.futuresOpen(margins.available().accountId(),
        margins.locked().accountId(), cash.accountId(), feeExpense.accountId(), initialMargin, fee);
    ensureNonNegative(ownerUserId, postings, cash, margins.available(), margins.locked());
    long ledgerVersion = transactionPort.reserveNextLedgerVersion(ownerUserId, lockedVersion);
    String transactionId = idGenerator.next();
    String detailId = idGenerator.next();
    LedgerTradeDetail detail = new LedgerTradeDetail(detailId, 1, instrument.instrumentId(), PositionEffect.OPEN,
        command.quantity(), null, command.pricePoints(), instrument.contractMultiplierCent(), instrument.maturityDate(),
        command.feeCent(), null);
    LedgerTransaction transaction = sourceType == LedgerSourceType.IMPORT
        ? LedgerTransaction.imported(transactionId, ownerUserId, LedgerTransactionType.FUTURES_OPEN,
            command.occurredOn(), importExportFileId, ledgerVersion, command.note(), postingFacts(postings), List.of(detail))
        : LedgerTransaction.original(transactionId, ownerUserId, LedgerTransactionType.FUTURES_OPEN,
            command.occurredOn(), ledgerVersion, command.note(), postingFacts(postings), List.of(detail));
    transactionPort.append(transaction);
    transactionEventPort.recordAppended(transaction);
    List<FuturesLot> lots = new ArrayList<>(positionPort.find(ownerUserId, margins.locked().accountId(),
        instrument.instrumentId()));
    lots.add(new FuturesLot(detailId, command.occurredOn(), command.quantity(), command.quantity(),
        command.pricePoints(), command.pricePoints(), command.occurredOn(), instrument.contractMultiplierCent(),
        command.initialMarginCent(), command.initialMarginCent(), CurrencyCode.CNY));
    positionPort.replace(ownerUserId, margins.locked().accountId(), instrument.instrumentId(), CurrencyCode.CNY,
        ledgerVersion, lots);
    return new FuturesOpenResult(transaction);
  }

  @Transactional(readOnly = true)
  public FuturesOpenPreviewResult preview(String ownerUserId, FuturesOpenCommand command) {
    validateCommand(ownerUserId, command);
    LedgerAccount cash = activeCnyCash(ownerUserId, command.cashAccountId());
    FuturesInstrument instrument = instrumentPort.findById(command.instrumentId())
        .orElseThrow(() -> new IllegalArgumentException("future contract was not found or is incomplete"));
    if (instrument.currency() != CurrencyCode.CNY || !command.occurredOn().isBefore(instrument.maturityDate())) {
      throw new IllegalArgumentException("future must use CNY and open before its maturity date");
    }
    MarginAccounts margins = resolveMarginAccounts(ownerUserId, cash, false);
    LedgerAccount feeExpense = activeSystemAccount(ownerUserId, cash, "SYS:FEE_EXPENSE:CNY",
        LedgerAccountKind.EXPENSE_FEE);
    BalancedPostings postings = LedgerPostingTemplates.futuresOpen(margins.available().accountId(),
        margins.locked().accountId(), cash.accountId(), feeExpense.accountId(), Money.of(command.initialMarginCent(),
            CurrencyCode.CNY), Money.of(command.feeCent(), CurrencyCode.CNY));
    ensureNonNegative(ownerUserId, postings, cash, margins.available(), margins.locked());
    return new FuturesOpenPreviewResult(CurrencyCode.CNY, previewPostings(postings, cash, margins.available(),
        margins.locked(), feeExpense), margins.provisioning());
  }

  private static void validateCommand(String ownerUserId, FuturesOpenCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank() || command == null || command.cashAccountId() == null
        || command.cashAccountId().isBlank() || command.instrumentId() == null || command.instrumentId().isBlank()
        || command.occurredOn() == null || command.initialMarginCent() <= 0 || command.feeCent() < 0) {
      throw new IllegalArgumentException("futures open command is invalid");
    }
    Quantity.of(command.quantity());
    Quantity.of(command.pricePoints());
    if (command.quantity().stripTrailingZeros().scale() > 0) {
      throw new IllegalArgumentException("futures quantity must be a positive whole lot count");
    }
    try {
      if (command.initialMarginCent() < command.quantity().longValueExact()) {
        throw new IllegalArgumentException("initial margin must allocate at least one minor unit to every futures lot");
      }
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("futures quantity exceeds supported whole-lot range", exception);
    }
    if (command.note() != null && command.note().length() > 1_000) {
      throw new IllegalArgumentException("note exceeds 1000 characters");
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

  private MarginAccounts resolveMarginAccounts(String ownerUserId, LedgerAccount cash, boolean persistMissing) {
    String availableCode = "MRGAV:" + cash.accountId();
    String lockedCode = "MRGLK:" + cash.accountId();
    List<String> provisioning = new ArrayList<>();
    LedgerAccount available = accountLookupPort.findByOwnerAndCode(ownerUserId, availableCode).orElse(null);
    LedgerAccount locked = accountLookupPort.findByOwnerAndCode(ownerUserId, lockedCode).orElse(null);
    if (available == null) {
      if (persistMissing) {
        accountMutationPort.insertSystemIfAbsent(LedgerAccount.newMarginAvailable(idGenerator.next(), ownerUserId,
            cash.accountId(), cash.currency()));
        available = accountLookupPort.findByOwnerAndCode(ownerUserId, availableCode)
            .orElseThrow(() -> new IllegalStateException("available margin account was not created"));
      } else {
        available = LedgerAccount.newMarginAvailable("PREVIEW:" + cash.accountId(), ownerUserId, cash.accountId(),
            cash.currency());
        provisioning.add(availableCode);
      }
    }
    if (locked == null) {
      if (persistMissing) {
        accountMutationPort.insertSystemIfAbsent(LedgerAccount.newMarginLocked(idGenerator.next(), ownerUserId,
            cash.accountId(), cash.currency()));
        locked = accountLookupPort.findByOwnerAndCode(ownerUserId, lockedCode)
            .orElseThrow(() -> new IllegalStateException("locked margin account was not created"));
      } else {
        locked = LedgerAccount.newMarginLocked("PREVIEW_LOCKED:" + cash.accountId(), ownerUserId,
            cash.accountId(), cash.currency());
        provisioning.add(lockedCode);
      }
    }
    validateMargin(available, cash, availableCode);
    validateMargin(locked, cash, lockedCode);
    return new MarginAccounts(available, locked, List.copyOf(provisioning));
  }

  private LedgerAccount activeSystemAccount(String ownerUserId, LedgerAccount cash, String accountCode,
      LedgerAccountKind expectedKind) {
    LedgerAccount account = accountLookupPort.findByOwnerAndCode(ownerUserId, accountCode)
        .orElseThrow(() -> new IllegalStateException("required system account was not provisioned"));
    if (account.accountKind() != expectedKind || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cash.currency()) {
      throw new IllegalStateException("required system account is invalid");
    }
    return account;
  }

  private static void validateMargin(LedgerAccount account, LedgerAccount cash, String expectedCode) {
    if (account.accountKind() != LedgerAccountKind.ASSET_MARGIN || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cash.currency() || !expectedCode.equals(account.accountCode())) {
      throw new IllegalStateException("margin account is invalid");
    }
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

  private static void apply(Map<String, LedgerAccount> byId, Map<String, Long> balances, String accountId,
      PostingSide side, Money amount) {
    LedgerAccount account = byId.get(accountId);
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
      LedgerAccount account = Objects.requireNonNull(byId.get(posting.accountId()));
      return new PreviewPosting(account.accountCode(), account.displayName(), posting.side(), posting.amount().cent(),
          posting.amount().currency());
    }).toList();
  }

  private record MarginAccounts(LedgerAccount available, LedgerAccount locked, List<String> provisioning) {
  }
}
