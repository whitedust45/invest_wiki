package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.Quantity;
import java.math.BigDecimal;
import java.util.Objects;

/** Read-only owner/account/instrument quantity reconstructed from FIFO facts at a business date. */
public record HistoricalFifoPosition(String cashAccountId, String instrumentId, CurrencyCode currency,
                                    BigDecimal quantity, long remainingCostCent) {
  public HistoricalFifoPosition {
    if (cashAccountId == null || cashAccountId.isBlank() || instrumentId == null || instrumentId.isBlank()) {
      throw new IllegalArgumentException("historical FIFO position identifiers are required");
    }
    Objects.requireNonNull(currency, "currency must not be null");
    quantity = Quantity.of(quantity);
    if (remainingCostCent < 0) {
      throw new IllegalArgumentException("remainingCostCent must not be negative");
    }
  }
}
