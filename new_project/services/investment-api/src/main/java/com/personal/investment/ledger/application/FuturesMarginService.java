package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.BalancedPostings;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerPostingTemplates;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.LedgerSourceType;
import com.personal.investment.ledger.domain.MarginDirection;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.Posting;
import com.personal.investment.ledger.domain.PostingSide;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the cash-to-available-margin movement and the paired margin-account provisioning. */
@Service
public class FuturesMarginService {
  private final LedgerCommandAccountPort accountLookupPort;
  private final LedgerAccountPort accountMutationPort;
  private final LedgerTransactionPort transactionPort;
  private final LedgerIdGenerator idGenerator;
  private final LedgerTransactionEventPort transactionEventPort;

  public FuturesMarginService(LedgerCommandAccountPort accountLookupPort, LedgerAccountPort accountMutationPort,
      LedgerTransactionPort transactionPort, LedgerIdGenerator idGenerator,
      LedgerTransactionEventPort transactionEventPort) {
    this.accountLookupPort = accountLookupPort;
    this.accountMutationPort = accountMutationPort;
    this.transactionPort = transactionPort;
    this.idGenerator = idGenerator;
    this.transactionEventPort = transactionEventPort;
  }

  @Transactional(readOnly = true)
  public FuturesMarginPreviewResult preview(String ownerUserId, FuturesMarginCommand command) {
    validate(ownerUserId, command);
    LedgerAccount cash = activeCash(ownerUserId, command.cashAccountId());
    MarginAccounts accounts = resolveMarginAccounts(ownerUserId, cash, false);
    BalancedPostings postings = LedgerPostingTemplates.futuresMargin(cash.accountId(),
        accounts.available().accountId(), command.direction(), command.amount());
    ensureNonNegativeBalances(ownerUserId, postings, cash, accounts.available());
    return new FuturesMarginPreviewResult(cash.currency(), previewPostings(postings, cash, accounts.available()),
        accounts.provisioning());
  }

  @Transactional(readOnly = true)
  public FuturesMarginPreviewResult previewByMinorUnit(String ownerUserId, String cashAccountId,
      java.time.LocalDate occurredOn, MarginDirection direction, long amountCent, String note) {
    LedgerAccount cash = activeCash(ownerUserId, cashAccountId);
    return preview(ownerUserId, new FuturesMarginCommand(cashAccountId, occurredOn, direction,
        Money.of(amountCent, cash.currency()), note));
  }

  @Transactional
  public LedgerTransaction move(String ownerUserId, FuturesMarginCommand command) {
    return move(ownerUserId, command, LedgerSourceType.MANUAL, null);
  }

  /** Appends a futures funding movement reconstructed from a confirmed import preview. */
  @Transactional
  public LedgerTransaction moveImported(String ownerUserId, FuturesMarginCommand command, String importExportFileId) {
    return move(ownerUserId, command, LedgerSourceType.IMPORT, importExportFileId);
  }

  private LedgerTransaction move(String ownerUserId, FuturesMarginCommand command, LedgerSourceType sourceType,
      String importExportFileId) {
    validate(ownerUserId, command);
    LedgerAccount cash = activeCash(ownerUserId, command.cashAccountId());
    long lockedVersion = transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    MarginAccounts accounts = resolveMarginAccounts(ownerUserId, cash, true);
    BalancedPostings postings = LedgerPostingTemplates.futuresMargin(cash.accountId(),
        accounts.available().accountId(), command.direction(), command.amount());
    ensureNonNegativeBalances(ownerUserId, postings, cash, accounts.available());
    long ledgerVersion = transactionPort.reserveNextLedgerVersion(ownerUserId, lockedVersion);
    LedgerTransaction transaction = sourceType == LedgerSourceType.IMPORT
        ? LedgerTransaction.imported(idGenerator.next(), ownerUserId, LedgerTransactionType.FUTURES_MARGIN,
            command.occurredOn(), importExportFileId, ledgerVersion, command.note(), postingFacts(postings))
        : LedgerTransaction.original(idGenerator.next(), ownerUserId, LedgerTransactionType.FUTURES_MARGIN,
            command.occurredOn(), ledgerVersion, command.note(), postingFacts(postings));
    transactionPort.append(transaction);
    transactionEventPort.recordAppended(transaction);
    return transaction;
  }

  @Transactional
  public LedgerTransaction moveByMinorUnit(String ownerUserId, String cashAccountId, java.time.LocalDate occurredOn,
      MarginDirection direction, long amountCent, String note) {
    LedgerAccount cash = activeCash(ownerUserId, cashAccountId);
    return move(ownerUserId, new FuturesMarginCommand(cashAccountId, occurredOn, direction,
        Money.of(amountCent, cash.currency()), note));
  }

  private MarginAccounts resolveMarginAccounts(String ownerUserId, LedgerAccount cash, boolean persistMissing) {
    String availableCode = "MRGAV:" + cash.accountId();
    String lockedCode = "MRGLK:" + cash.accountId();
    LedgerAccount available = accountLookupPort.findByOwnerAndCode(ownerUserId, availableCode).orElse(null);
    LedgerAccount locked = accountLookupPort.findByOwnerAndCode(ownerUserId, lockedCode).orElse(null);
    List<String> provisioning = new ArrayList<>();
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
    validateMarginAccount(available, cash, availableCode);
    validateMarginAccount(locked, cash, lockedCode);
    return new MarginAccounts(available, locked, List.copyOf(provisioning));
  }

  private static void validate(String ownerUserId, FuturesMarginCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank() || command == null || command.cashAccountId() == null
        || command.cashAccountId().isBlank() || command.occurredOn() == null || command.direction() == null
        || command.amount() == null || !command.amount().isPositive()) {
      throw new IllegalArgumentException("futures margin command is invalid");
    }
    if (command.note() != null && command.note().length() > 1_000) {
      throw new IllegalArgumentException("note exceeds 1000 characters");
    }
  }

  private LedgerAccount activeCash(String ownerUserId, String cashAccountId) {
    LedgerAccount account = accountLookupPort.findByIdAndOwner(cashAccountId, ownerUserId)
        .orElseThrow(() -> new IllegalArgumentException("cash account was not found"));
    if (account.accountKind() != LedgerAccountKind.ASSET_CASH || account.status() != LedgerAccountStatus.ACTIVE) {
      throw new IllegalArgumentException("cash account must be active");
    }
    if (account.currency() != CurrencyCode.CNY) {
      throw new IllegalArgumentException("CFFEX futures margin requires a CNY cash account");
    }
    return account;
  }

  private static void validateMarginAccount(LedgerAccount margin, LedgerAccount cash, String expectedCode) {
    if (margin.accountKind() != LedgerAccountKind.ASSET_MARGIN || margin.status() != LedgerAccountStatus.ACTIVE
        || margin.currency() != cash.currency() || !margin.accountCode().equals(expectedCode)) {
      throw new IllegalStateException("paired margin account is invalid");
    }
  }

  private void ensureNonNegativeBalances(String ownerUserId, BalancedPostings proposed, LedgerAccount... accounts) {
    Map<String, LedgerAccount> accountById = new HashMap<>();
    Map<String, Long> balances = new HashMap<>();
    for (LedgerAccount account : accounts) {
      accountById.put(account.accountId(), account);
      balances.put(account.accountId(), 0L);
    }
    for (LedgerPostingFact fact : transactionPort.findPostingFactsByOwner(ownerUserId)) {
      apply(accountById, balances, fact.accountId(), fact.side(), fact.amount());
    }
    for (Posting posting : proposed.postings()) {
      apply(accountById, balances, posting.accountId(), posting.side(), posting.amount());
    }
    balances.forEach((accountId, balance) -> {
      if (balance < 0) {
        throw new InsufficientBalanceException(accountId);
      }
    });
  }

  private static void apply(Map<String, LedgerAccount> accountById, Map<String, Long> balances, String accountId,
      PostingSide side, Money amount) {
    LedgerAccount account = accountById.get(accountId);
    if (account == null) {
      return;
    }
    if (account.currency() != amount.currency()) {
      throw new IllegalStateException("margin posting currency does not match account currency");
    }
    try {
      balances.put(accountId, side == PostingSide.DEBIT
          ? Math.addExact(balances.get(accountId), amount.cent())
          : Math.subtractExact(balances.get(accountId), amount.cent()));
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("margin balance overflow", exception);
    }
  }

  private List<LedgerPostingFact> postingFacts(BalancedPostings postings) {
    int[] postingNo = {1};
    return postings.postings().stream().map(posting -> new LedgerPostingFact(idGenerator.next(), posting.accountId(),
        postingNo[0]++, posting.side(), posting.amount())).toList();
  }

  private static List<PreviewPosting> previewPostings(BalancedPostings postings, LedgerAccount cash,
      LedgerAccount available) {
    Map<String, LedgerAccount> accounts = Map.of(cash.accountId(), cash, available.accountId(), available);
    return postings.postings().stream().map(posting -> {
      LedgerAccount account = Objects.requireNonNull(accounts.get(posting.accountId()));
      return new PreviewPosting(account.accountCode(), account.displayName(), posting.side(), posting.amount().cent(),
          posting.amount().currency());
    }).toList();
  }

  private record MarginAccounts(LedgerAccount available, LedgerAccount locked, List<String> provisioning) {
  }
}
