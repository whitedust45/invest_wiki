package com.personal.investment.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FifoCostAllocatorTest {
  @Test
  void consumesLotsByOccurredOnTransactionAndDetailWhilePreservingExactRemainingCost() {
    FifoLot later = lot("tx-b", 1, LocalDate.of(2026, 7, 2), "1", 50);
    FifoLot earlier = lot("tx-a", 2, LocalDate.of(2026, 7, 1), "2", 100);

    FifoAllocation allocation = FifoCostAllocator.allocate(List.of(later, earlier), quantity("2.5"));

    assertThat(allocation.allocatedCostCent()).isEqualTo(125);
    assertThat(allocation.consumptions()).extracting(FifoLotConsumption::sourceTransactionId)
        .containsExactly("tx-a", "tx-b");
    assertThat(allocation.remainingLots()).extracting(FifoLot::remainingQuantity)
        .containsExactly(BigDecimal.ZERO.setScale(8), new BigDecimal("0.50000000"));
    assertThat(allocation.remainingLots()).extracting(FifoLot::remainingCostCent).containsExactly(0L, 25L);
  }

  @Test
  void assignsRemainderToFinalConsumptionSoAFullSaleAlwaysClearsTheLotCost() {
    FifoLot lot = lot("tx-a", 1, LocalDate.of(2026, 7, 1), "3", 10);

    FifoAllocation first = FifoCostAllocator.allocate(List.of(lot), quantity("1"));
    FifoAllocation second = FifoCostAllocator.allocate(first.remainingLots(), quantity("2"));

    assertThat(first.allocatedCostCent()).isEqualTo(3);
    assertThat(second.allocatedCostCent()).isEqualTo(7);
    assertThat(second.remainingLots()).singleElement().satisfies(remaining -> {
      assertThat(remaining.remainingQuantity()).isEqualByComparingTo("0");
      assertThat(remaining.remainingCostCent()).isZero();
    });
  }

  @Test
  void rejectsOversellAndFractionalCentGrossCost() {
    FifoLot lot = lot("tx-a", 1, LocalDate.of(2026, 7, 1), "1", 100);

    assertThatThrownBy(() -> FifoCostAllocator.allocate(List.of(lot), quantity("1.00000001")))
        .isInstanceOf(InsufficientPositionException.class)
        .hasMessageContaining("INSUFFICIENT_POSITION");
    assertThatThrownBy(() -> SpotTradeMath.grossCostCent(quantity("0.1"), 1))
        .isInstanceOf(PricePrecisionException.class)
        .hasMessageContaining("PRICE_PRECISION_INVALID");
  }

  private static FifoLot lot(String transactionId, int detailNo, LocalDate occurredOn, String openedQuantity,
      long costCent) {
    return new FifoLot("detail-" + transactionId + "-" + detailNo, transactionId, detailNo, occurredOn,
        quantity(openedQuantity), quantity(openedQuantity), costCent, costCent);
  }

  private static BigDecimal quantity(String value) {
    return Quantity.of(new BigDecimal(value));
  }
}
