package com.personal.investment.ledger.domain;

import java.util.Objects;

/** Ledger account aggregate. The business ID, rather than its physical database key, is public. */
public record LedgerAccount(
    String accountId,
    String ownerUserId,
    String accountCode,
    LedgerAccountKind accountKind,
    CurrencyCode currency,
    String displayName,
    LedgerAccountStatus status,
    long version) {
  public LedgerAccount {
    requireText(accountId, "accountId");
    requireText(ownerUserId, "ownerUserId");
    requireText(accountCode, "accountCode");
    Objects.requireNonNull(accountKind, "accountKind must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    requireText(displayName, "displayName");
    if (displayName.length() > 128) {
      throw new IllegalArgumentException("displayName exceeds 128 characters");
    }
    Objects.requireNonNull(status, "status must not be null");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  public static LedgerAccount newCash(
      String accountId, String ownerUserId, String displayName, CurrencyCode currency) {
    return new LedgerAccount(accountId, ownerUserId, "CASH:" + accountId, LedgerAccountKind.ASSET_CASH,
        currency, displayName, LedgerAccountStatus.ACTIVE, 0);
  }

  public static LedgerAccount newSystem(
      String accountId, String ownerUserId, SystemLedgerAccount systemAccount, CurrencyCode currency) {
    Objects.requireNonNull(systemAccount, "systemAccount must not be null");
    return new LedgerAccount(accountId, ownerUserId, systemAccount.accountCode(currency),
        systemAccount.accountKind(), currency, systemAccount.displayName(), LedgerAccountStatus.ACTIVE, 0);
  }

  public static LedgerAccount newInvestment(String accountId, String ownerUserId, String cashAccountId,
      String instrumentId, CurrencyCode currency) {
    requireText(cashAccountId, "cashAccountId");
    requireText(instrumentId, "instrumentId");
    String accountCode = "INV:" + cashAccountId + ":" + instrumentId;
    if (accountCode.length() >= 64) {
      throw new IllegalArgumentException("investment account code must be shorter than 64 characters");
    }
    return new LedgerAccount(accountId, ownerUserId, accountCode, LedgerAccountKind.ASSET_INVESTMENT, currency,
        "投资成本 " + instrumentId, LedgerAccountStatus.ACTIVE, 0);
  }

  public static LedgerAccount newMarginAvailable(String accountId, String ownerUserId, String cashAccountId,
      CurrencyCode currency) {
    return newMargin(accountId, ownerUserId, "MRGAV", "可用保证金", cashAccountId, currency);
  }

  public static LedgerAccount newMarginLocked(String accountId, String ownerUserId, String cashAccountId,
      CurrencyCode currency) {
    return newMargin(accountId, ownerUserId, "MRGLK", "锁定保证金", cashAccountId, currency);
  }

  private static LedgerAccount newMargin(String accountId, String ownerUserId, String prefix, String displayName,
      String cashAccountId, CurrencyCode currency) {
    requireText(cashAccountId, "cashAccountId");
    String accountCode = prefix + ":" + cashAccountId;
    if (accountCode.length() >= 64) {
      throw new IllegalArgumentException("margin account code must be shorter than 64 characters");
    }
    return new LedgerAccount(accountId, ownerUserId, accountCode, LedgerAccountKind.ASSET_MARGIN, currency,
        displayName, LedgerAccountStatus.ACTIVE, 0);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
