package com.personal.investment.portfolio.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/** Projection input only; the append-only valuation fact remains the source of truth. */
public record PortfolioManualValuation(String instrumentId, CurrencyCode currency, LocalDate valuationDate,
                                      Long unitPriceCent, Long marketValueCent, short priority, Instant validUntil,
                                      LocalDateTime createdAt) {
  public PortfolioManualValuation {
    if (instrumentId == null || instrumentId.isBlank()) {
      throw new IllegalArgumentException("instrumentId must not be blank");
    }
    Objects.requireNonNull(currency, "currency must not be null");
    Objects.requireNonNull(valuationDate, "valuationDate must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    if ((unitPriceCent == null) == (marketValueCent == null)) {
      throw new IllegalArgumentException("exactly one valuation amount is required");
    }
    if (unitPriceCent != null && unitPriceCent <= 0 || marketValueCent != null && marketValueCent <= 0) {
      throw new IllegalArgumentException("valuation amount must be positive");
    }
  }
}
