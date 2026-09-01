package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.PostingSide;
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerAccountService {
  private final LedgerAccountPort accountPort;
  private final LedgerAccountLifecyclePort lifecyclePort;
  private final LedgerTransactionPort transactionPort;
  private final LedgerIdGenerator idGenerator;

  @Autowired
  public LedgerAccountService(LedgerAccountLifecyclePort lifecyclePort, LedgerTransactionPort transactionPort,
      LedgerIdGenerator idGenerator) {
    this.lifecyclePort = lifecyclePort;
    this.accountPort = lifecyclePort;
    this.transactionPort = transactionPort;
    this.idGenerator = idGenerator;
  }

  @Transactional
  public LedgerAccount disableCashAccount(String ownerUserId, String cashAccountId, long expectedVersion) {
    requireText(ownerUserId, "ownerUserId");
    requireText(cashAccountId, "cashAccountId");
    if (expectedVersion < 0) {
      throw new IllegalArgumentException("expectedVersion must not be negative");
    }
    transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    LedgerAccount account = lifecyclePort.findByIdAndOwner(cashAccountId, ownerUserId)
        .orElseThrow(() -> new IllegalArgumentException("cash account was not found"));
    if (account.accountKind() != LedgerAccountKind.ASSET_CASH || account.status() != LedgerAccountStatus.ACTIVE) {
      throw new AccountDisableRejectedException("cash account is not active");
    }
    if (account.version() != expectedVersion) {
      throw new AccountVersionConflictException();
    }
    if (balance(ownerUserId, account.accountId()) != 0) {
      throw new AccountDisableRejectedException("cash account balance must be zero");
    }
    if (lifecyclePort.hasOpenSpotPosition(ownerUserId, cashAccountId)
        || lifecyclePort.hasOpenFuturesPosition(ownerUserId, cashAccountId)) {
      throw new AccountDisableRejectedException("cash account has open positions");
    }
    ensureMarginBalancesAreZero(ownerUserId, cashAccountId);
    if (lifecyclePort.hasActiveImportReferencingCashAccount(ownerUserId, cashAccountId)) {
      throw new AccountDisableRejectedException("cash account is referenced by an active import job");
    }
    if (!lifecyclePort.disableIfCurrentVersion(ownerUserId, cashAccountId, expectedVersion)) {
      throw new AccountVersionConflictException();
    }
    return new LedgerAccount(account.accountId(), account.ownerUserId(), account.accountCode(), account.accountKind(),
        account.currency(), account.displayName(), LedgerAccountStatus.DISABLED, Math.addExact(expectedVersion, 1));
  }

  private void ensureMarginBalancesAreZero(String ownerUserId, String cashAccountId) {
    for (String code : List.of("MRGAV:" + cashAccountId, "MRGLK:" + cashAccountId)) {
      lifecyclePort.findByOwnerAndCode(ownerUserId, code).ifPresent(account -> {
        if (balance(ownerUserId, account.accountId()) != 0) {
          throw new AccountDisableRejectedException("cash account has non-zero margin balance");
        }
      });
    }
  }

  private long balance(String ownerUserId, String accountId) {
    long balance = 0;
    for (LedgerPostingFact posting : transactionPort.findPostingFactsByOwner(ownerUserId)) {
      if (!posting.accountId().equals(accountId)) {
        continue;
      }
      try {
        balance = posting.side() == PostingSide.DEBIT ? Math.addExact(balance, posting.amount().cent())
            : Math.subtractExact(balance, posting.amount().cent());
      } catch (ArithmeticException exception) {
        throw new IllegalStateException("account balance overflow", exception);
      }
    }
    return balance;
  }

  public LedgerAccountService(LedgerAccountPort accountPort, LedgerIdGenerator idGenerator) {
    this.accountPort = accountPort;
    this.lifecyclePort = null;
    this.transactionPort = null;
    this.idGenerator = idGenerator;
  }

  @Transactional
  public LedgerAccount createCashAccount(String ownerUserId, String displayName, CurrencyCode currency) {
    requireText(ownerUserId, "ownerUserId");
    requireText(displayName, "displayName");
    Objects.requireNonNull(currency, "currency must not be null");
    // Shares the owner-local coordination lock with empty-workspace recovery. The compatibility constructor used by
    // narrow unit tests intentionally has no transaction port.
    if (transactionPort != null) {
      transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    }
    for (SystemLedgerAccount systemAccount : SystemLedgerAccount.values()) {
      accountPort.insertSystemIfAbsent(
          LedgerAccount.newSystem(idGenerator.next(), ownerUserId, systemAccount, currency));
    }
    LedgerAccount cashAccount = LedgerAccount.newCash(idGenerator.next(), ownerUserId, displayName, currency);
    accountPort.insert(cashAccount);
    return cashAccount;
  }

  @Transactional(readOnly = true)
  public List<LedgerAccount> listCashAccounts(String ownerUserId) {
    requireText(ownerUserId, "ownerUserId");
    return accountPort.findCashAccountsByOwner(ownerUserId);
  }

  @Transactional(readOnly = true)
  public List<LedgerAccount> listAllAccounts(String ownerUserId) {
    requireText(ownerUserId, "ownerUserId");
    return accountPort.findAllAccountsByOwner(ownerUserId);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
