package com.personal.investment.portfolio.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.Quantity;
import java.math.BigDecimal;
import java.util.Objects;

/** A non-zero position reconstructed from immutable Ledger facts for one cash account. */
public record PortfolioOpenPosition(String cashAccountId, String instrumentId, CurrencyCode currency,
                                    BigDecimal quantity, Long costCent) {
  public PortfolioOpenPosition {
    if (cashAccountId == null || cashAccountId.isBlank() || instrumentId == null || instrumentId.isBlank()) {
      throw new IllegalArgumentException("portfolio position identifiers are required");
    }
    Objects.requireNonNull(currency, "currency must not be null");
    quantity = Quantity.of(quantity);
    if (costCent != null && costCent < 0) {
      throw new IllegalArgumentException("costCent must not be negative");
    }
  }

  public PortfolioOpenPosition(String cashAccountId, String instrumentId, CurrencyCode currency,
                               BigDecimal quantity) {
    this(cashAccountId, instrumentId, currency, quantity, null);
  }
}
