package com.personal.investment.ledger.domain;

import java.util.List;

public record FifoAllocation(long allocatedCostCent, List<FifoLotConsumption> consumptions,
                             List<FifoLot> remainingLots) {
  public FifoAllocation {
    if (allocatedCostCent < 0) {
      throw new IllegalArgumentException("allocatedCostCent must not be negative");
    }
    consumptions = List.copyOf(consumptions);
    remainingLots = List.copyOf(remainingLots);
  }
}
