package com.personal.investment.reporting.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.portfolio.application.PortfolioValuationStatus;
import java.util.List;
import java.util.Objects;

/** Single-native-currency allocation read model. shareBasisPoints is a rendering ratio, never a money amount. */
public record PortfolioAllocation(CurrencyCode currency, PortfolioValuationStatus valuationStatus,
                                  Long marketValueCent, List<Slice> slices) {
  public PortfolioAllocation {
    Objects.requireNonNull(currency, "currency must not be null");
    Objects.requireNonNull(valuationStatus, "valuationStatus must not be null");
    if (marketValueCent != null && marketValueCent < 0) {
      throw new IllegalArgumentException("marketValueCent must not be negative");
    }
    slices = List.copyOf(slices);
  }

  public record Slice(String instrumentId, long marketValueCent, int shareBasisPoints) {
    public Slice {
      if (instrumentId == null || !instrumentId.matches("^[0-9A-HJKMNP-TV-Z]{26}$")) {
        throw new IllegalArgumentException("instrumentId must be a ULID");
      }
      if (marketValueCent < 0 || shareBasisPoints < 0 || shareBasisPoints > 10_000) {
        throw new IllegalArgumentException("allocation slice is invalid");
      }
    }
  }
}
