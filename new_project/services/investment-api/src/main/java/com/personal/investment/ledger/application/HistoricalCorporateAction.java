package com.personal.investment.ledger.application;

import java.time.LocalDate;
import java.util.Objects;

/** Immutable non-monetary corporate-action fact included in spot-projection replay. */
public record HistoricalCorporateAction(String transactionId, LocalDate effectiveOn, String instrumentId,
                                       CorporateActionType actionType, long ratioNumerator,
                                       long ratioDenominator) {
  public HistoricalCorporateAction {
    requireText(transactionId, "transactionId");
    Objects.requireNonNull(effectiveOn, "effectiveOn must not be null");
    requireText(instrumentId, "instrumentId");
    Objects.requireNonNull(actionType, "actionType must not be null");
    if (ratioNumerator <= 0 || ratioDenominator <= 0) {
      throw new IllegalArgumentException("corporate action ratio must be positive");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
