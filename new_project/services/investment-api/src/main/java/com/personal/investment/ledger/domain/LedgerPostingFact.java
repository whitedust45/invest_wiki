package com.personal.investment.ledger.domain;

import java.util.Objects;

/** Immutable persisted posting fact; money remains a positive native-currency amount. */
public record LedgerPostingFact(
    String postingId, String accountId, int postingNo, PostingSide side, Money amount) {
  public LedgerPostingFact {
    requireText(postingId, "postingId");
    requireText(accountId, "accountId");
    if (postingNo < 1) {
      throw new IllegalArgumentException("postingNo must start at 1");
    }
    Objects.requireNonNull(side, "posting side must not be null");
    Objects.requireNonNull(amount, "posting amount must not be null");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("posting amount must be positive");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
