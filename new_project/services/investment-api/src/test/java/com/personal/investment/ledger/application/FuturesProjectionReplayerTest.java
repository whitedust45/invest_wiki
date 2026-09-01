package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FuturesProjectionReplayerTest {
  @Test
  void rebuildsOpenLotsSettlementBaselinesAndPartialClosesFromImmutableHistory() {
    CapturingProjection projection = new CapturingProjection();
    FuturesProjectionReplayer replayer = new FuturesProjectionReplayer(ownerUserId -> List.of(
        event("open", LedgerTransactionType.FUTURES_OPEN, LocalDate.of(2026, 7, 1), "detail-open", "2", "100", 2_000),
        event("settle", LedgerTransactionType.FUTURES_DAILY_SETTLEMENT, LocalDate.of(2026, 7, 2), "detail-settle", "2", "110", 0),
        event("close", LedgerTransactionType.FUTURES_CLOSE, LocalDate.of(2026, 7, 3), "detail-close", "1", "120", 0)),
        projection);

    replayer.rebuild("owner", 9L);

    assertThat(projection.sourceLedgerVersion).isEqualTo(9L);
    assertThat(projection.projections).hasSize(1);
    FuturesLotProjection position = projection.projections.getFirst();
    assertThat(position.cashAccountId()).isEqualTo("cash");
    assertThat(position.instrumentId()).isEqualTo("future");
    assertThat(position.lots()).singleElement().satisfies(lot -> {
      assertThat(lot.remainingQuantity()).isEqualByComparingTo("1.00000000");
      assertThat(lot.remainingInitialMarginCent()).isEqualTo(1_000L);
      assertThat(lot.lastSettlementPricePoints()).isEqualByComparingTo("110");
      assertThat(lot.lastSettlementOn()).isEqualTo(LocalDate.of(2026, 7, 2));
    });
  }

  private static HistoricalFuturesTrade event(String transactionId, LedgerTransactionType type, LocalDate occurredOn,
      String tradeDetailId, String quantity, String pricePoints, long initialMarginCent) {
    return new HistoricalFuturesTrade(transactionId, type, occurredOn, "cash", "locked", "future", tradeDetailId, 1,
        new BigDecimal(quantity), new BigDecimal(pricePoints), 200L, initialMarginCent, CurrencyCode.CNY);
  }

  private static final class CapturingProjection implements FuturesProjectionRebuildPort {
    private long sourceLedgerVersion;
    private List<FuturesLotProjection> projections = new ArrayList<>();

    @Override
    public void replaceOwnerProjection(String ownerUserId, long sourceLedgerVersion,
        List<FuturesLotProjection> projections) {
      this.sourceLedgerVersion = sourceLedgerVersion;
      this.projections = List.copyOf(projections);
    }
  }
}
