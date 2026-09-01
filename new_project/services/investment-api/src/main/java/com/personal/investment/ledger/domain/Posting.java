package com.personal.investment.ledger.domain;

import java.util.Objects;

/** A positive, one-sided posting to a semantic ledger account identifier. */
public record Posting(String accountId, PostingSide side, Money amount) {
  public Posting {
    if (accountId == null || accountId.isBlank()) {
      throw new IllegalArgumentException("accountId must not be blank");
    }
    Objects.requireNonNull(side, "posting side must not be null");
    Objects.requireNonNull(amount, "posting amount must not be null");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("posting amount must be positive");
    }
  }
}
