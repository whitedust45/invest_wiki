package com.personal.investment.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic lot allocation with all rounding remainder assigned to the final consumption. */
public final class FifoCostAllocator {
  private static final Comparator<FifoLot> FIFO_ORDER = Comparator.comparing(FifoLot::occurredOn)
      .thenComparing(FifoLot::sourceTransactionId).thenComparingInt(FifoLot::detailNo);

  private FifoCostAllocator() {
  }

  public static FifoAllocation allocate(List<FifoLot> lots, BigDecimal requestedQuantity) {
    Objects.requireNonNull(lots, "lots must not be null");
    BigDecimal remainingToConsume = Quantity.of(requestedQuantity);
    List<FifoLot> orderedLots = lots.stream().sorted(FIFO_ORDER).toList();
    List<FifoLot> updated = new ArrayList<>(orderedLots.size());
    List<FifoLotConsumption> consumptions = new ArrayList<>();
    long totalCost = 0;
    for (FifoLot lot : orderedLots) {
      BigDecimal available = lot.remainingQuantity();
      if (remainingToConsume.signum() == 0 || available.signum() == 0) {
        updated.add(lot);
        continue;
      }
      BigDecimal consumed = remainingToConsume.min(available);
      long allocatedCost = allocatedCost(lot, consumed);
      try {
        totalCost = Math.addExact(totalCost, allocatedCost);
      } catch (ArithmeticException exception) {
        throw new PricePrecisionException();
      }
      updated.add(lot.consume(consumed, allocatedCost));
      consumptions.add(new FifoLotConsumption(lot.sourceTransactionId(), lot.detailNo(), consumed, allocatedCost));
      remainingToConsume = remainingToConsume.subtract(consumed);
    }
    if (remainingToConsume.signum() != 0) {
      throw new InsufficientPositionException();
    }
    return new FifoAllocation(totalCost, consumptions, updated);
  }

  private static long allocatedCost(FifoLot lot, BigDecimal consumedQuantity) {
    if (consumedQuantity.compareTo(lot.remainingQuantity()) == 0) {
      return lot.remainingCostCent();
    }
    try {
      return BigDecimal.valueOf(lot.remainingCostCent()).multiply(consumedQuantity)
          .divide(lot.remainingQuantity(), 0, RoundingMode.DOWN).longValueExact();
    } catch (ArithmeticException exception) {
      throw new PricePrecisionException();
    }
  }
}
