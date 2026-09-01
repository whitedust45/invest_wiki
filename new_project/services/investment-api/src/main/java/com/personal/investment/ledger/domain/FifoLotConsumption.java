package com.personal.investment.ledger.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record FifoLotConsumption(String sourceTransactionId, int detailNo, BigDecimal quantity,
                                 long allocatedCostCent) {
  public FifoLotConsumption {
    if (sourceTransactionId == null || sourceTransactionId.isBlank() || detailNo < 1) {
      throw new IllegalArgumentException("FIFO source must be present");
    }
    quantity = Quantity.of(quantity);
    if (allocatedCostCent < 0) {
      throw new IllegalArgumentException("allocatedCostCent must not be negative");
    }
  }
}
