package com.personal.investment.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** FIFO futures lot with the settlement baseline and exact locked-margin remainder. */
public record FuturesLot(String sourceTradeDetailId, LocalDate openedOn, BigDecimal openedQuantity,
                         BigDecimal remainingQuantity, BigDecimal openPricePoints,
                         BigDecimal lastSettlementPricePoints, LocalDate lastSettlementOn, long contractMultiplierCent,
                         long allocatedInitialMarginCent, long remainingInitialMarginCent, CurrencyCode currency) {
  public FuturesLot {
    require(sourceTradeDetailId, "sourceTradeDetailId");
    Objects.requireNonNull(openedOn, "openedOn must not be null");
    openedQuantity = Quantity.of(openedQuantity);
    remainingQuantity = Quantity.of(remainingQuantity);
    if (openedQuantity.stripTrailingZeros().scale() > 0 || remainingQuantity.stripTrailingZeros().scale() > 0
        || remainingQuantity.compareTo(openedQuantity) > 0) {
      throw new IllegalArgumentException("futures quantities must be positive whole lots and bounded");
    }
    openPricePoints = Quantity.of(openPricePoints);
    lastSettlementPricePoints = Quantity.of(lastSettlementPricePoints);
    Objects.requireNonNull(lastSettlementOn, "lastSettlementOn must not be null");
    if (lastSettlementOn.isBefore(openedOn)) {
      throw new IllegalArgumentException("lastSettlementOn must not precede futures lot open date");
    }
    if (contractMultiplierCent <= 0 || allocatedInitialMarginCent <= 0 || remainingInitialMarginCent < 0
        || remainingInitialMarginCent > allocatedInitialMarginCent) {
      throw new IllegalArgumentException("futures lot monetary snapshot is invalid");
    }
    Objects.requireNonNull(currency, "currency must not be null");
  }

  private static void require(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
