package com.personal.investment.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Replayable position-cost fact ordered solely by immutable transaction facts. */
public record FifoLot(
    String sourceTradeDetailId,
    String sourceTransactionId,
    int detailNo,
    LocalDate occurredOn,
    BigDecimal openedQuantity,
    BigDecimal remainingQuantity,
    long openedCostCent,
    long remainingCostCent) {
  public FifoLot {
    requireText(sourceTradeDetailId, "sourceTradeDetailId");
    requireText(sourceTransactionId, "sourceTransactionId");
    if (detailNo < 1) {
      throw new IllegalArgumentException("detailNo must be positive");
    }
    Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    openedQuantity = Quantity.of(openedQuantity);
    Objects.requireNonNull(remainingQuantity, "remainingQuantity must not be null");
    if (remainingQuantity.signum() < 0 || remainingQuantity.scale() > 8) {
      throw new IllegalArgumentException("remainingQuantity must be nonnegative with at most 8 decimals");
    }
    remainingQuantity = remainingQuantity.setScale(8);
    if (remainingQuantity.compareTo(openedQuantity) > 0) {
      throw new IllegalArgumentException("remainingQuantity must not exceed openedQuantity");
    }
    if (openedCostCent < 0 || remainingCostCent < 0 || remainingCostCent > openedCostCent) {
      throw new IllegalArgumentException("lot costs must be nonnegative and remaining cost bounded");
    }
    if (remainingQuantity.signum() == 0 && remainingCostCent != 0) {
      throw new IllegalArgumentException("closed lot must not retain cost");
    }
  }

  public FifoLot consume(BigDecimal consumedQuantity, long allocatedCostCent) {
    BigDecimal normalized = Quantity.of(consumedQuantity);
    if (normalized.compareTo(remainingQuantity) > 0 || allocatedCostCent < 0
        || allocatedCostCent > remainingCostCent) {
      throw new IllegalArgumentException("invalid FIFO lot consumption");
    }
    BigDecimal afterQuantity = remainingQuantity.subtract(normalized);
    long afterCost = remainingCostCent - allocatedCostCent;
    if (afterQuantity.signum() == 0 && afterCost != 0) {
      throw new IllegalArgumentException("final FIFO consumption must clear remaining cost");
    }
    return new FifoLot(sourceTradeDetailId, sourceTransactionId, detailNo, occurredOn, openedQuantity,
        afterQuantity, openedCostCent, afterCost);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
