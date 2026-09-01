package com.personal.investment.portfolio.domain;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable, append-only manual valuation fact. It never changes ledger cost or lots. */
public record ManualValuation(
    String manualValuationId,
    String ownerUserId,
    String instrumentId,
    LocalDate valuationDate,
    CurrencyCode currency,
    Long unitPriceCent,
    Long marketValueCent,
    short priority,
    Instant validUntil,
    String note,
    String createdByUserId) {
  public static final short MANUAL_PRIORITY = 100;

  public ManualValuation {
    requireText(manualValuationId, "manualValuationId");
    requireText(ownerUserId, "ownerUserId");
    requireText(instrumentId, "instrumentId");
    Objects.requireNonNull(valuationDate, "valuationDate must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    if ((unitPriceCent == null) == (marketValueCent == null)) {
      throw new IllegalArgumentException("exactly one of unitPriceCent or marketValueCent is required");
    }
    if (unitPriceCent != null && unitPriceCent <= 0) {
      throw new IllegalArgumentException("unitPriceCent must be positive");
    }
    if (marketValueCent != null && marketValueCent <= 0) {
      throw new IllegalArgumentException("marketValueCent must be positive");
    }
    if (priority != MANUAL_PRIORITY) {
      throw new IllegalArgumentException("manual valuation priority must be 100");
    }
    if (note != null && note.length() > 1_000) {
      throw new IllegalArgumentException("note exceeds 1000 characters");
    }
    requireText(createdByUserId, "createdByUserId");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
