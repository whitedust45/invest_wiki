package com.personal.investment.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.portfolio.application.PortfolioAccountBalance;
import com.personal.investment.portfolio.application.PortfolioManualValuation;
import com.personal.investment.portfolio.application.PortfolioOpenPosition;
import com.personal.investment.portfolio.application.PortfolioOverviewPort;
import com.personal.investment.portfolio.application.PortfolioOverviewService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioHistoryServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-31T01:00:00Z"), ZoneOffset.UTC);

  @Test
  void recordsCashOnlyPortfolioWithoutInventingMarketValue() {
    InMemoryHistory history = new InMemoryHistory();
    PortfolioOverviewService overview = new PortfolioOverviewService(new CashOnlyPortfolio(), missingInstruments(), CLOCK);
    PortfolioHistoryService service = new PortfolioHistoryService(overview, history, CLOCK);

    var result = service.snapshot(OWNER, LocalDate.of(2026, 7, 31));

    assertThat(result.persistedCount()).isEqualTo(1);
    assertThat(result.skippedUnvaluedCurrencyCount()).isZero();
    assertThat(history.points).singleElement().satisfies(point -> {
      assertThat(point.currency()).isEqualTo(CurrencyCode.CNY);
      assertThat(point.cashCent()).isEqualTo(12_345L);
      assertThat(point.marketValueCent()).isZero();
      assertThat(point.netAssetCent()).isEqualTo(12_345L);
      assertThat(point.sourceLedgerVersion()).isEqualTo(7L);
    });
  }

  @Test
  void doesNotPersistAnUnvaluedOpenPositionAsCostOrZero() {
    InMemoryHistory history = new InMemoryHistory();
    PortfolioOverviewService overview = new PortfolioOverviewService(new UnvaluedPositionPortfolio(), missingInstruments(), CLOCK);
    PortfolioHistoryService service = new PortfolioHistoryService(overview, history, CLOCK);

    var result = service.snapshot(OWNER, LocalDate.of(2026, 7, 31));

    assertThat(result.persistedCount()).isZero();
    assertThat(result.skippedUnvaluedCurrencyCount()).isEqualTo(1);
    assertThat(history.points).isEmpty();
  }

  @Test
  void createsExactServerSideChartCoordinatesWithoutJavaScriptMoneyMath() {
    PortfolioHistoryService service = new PortfolioHistoryService(
        new PortfolioOverviewService(new CashOnlyPortfolio(), missingInstruments(), CLOCK), new InMemoryHistory(), CLOCK);
    List<PortfolioHistoryPoint> points = List.of(
        new PortfolioHistoryPoint("01K8D43J4YFN7X9R2B6C8M0V3A", CurrencyCode.USD, LocalDate.of(2026, 7, 29),
            100L, 100L, 0L, 1L, CLOCK.instant()),
        new PortfolioHistoryPoint("01K8D43J4YFN7X9R2B6C8M0V3B", CurrencyCode.USD, LocalDate.of(2026, 7, 30),
            250L, 250L, 0L, 2L, CLOCK.instant()),
        new PortfolioHistoryPoint("01K8D43J4YFN7X9R2B6C8M0V3C", CurrencyCode.USD, LocalDate.of(2026, 7, 31),
            400L, 400L, 0L, 3L, CLOCK.instant()));

    assertThat(service.chart(points)).extracting(PortfolioHistoryChartPoint::netAssetBasisPoints)
        .containsExactly(0, 5_000, 10_000);
  }

  private static InstrumentPort missingInstruments() {
    return new InstrumentPort() {
      @Override public java.util.Optional<com.personal.investment.market.domain.Instrument> findByNaturalKey(
          String market, String exchange, String symbol) {
        return java.util.Optional.empty();
      }
      @Override public java.util.Optional<com.personal.investment.market.domain.Instrument> findById(String instrumentId) {
        return java.util.Optional.empty();
      }
      @Override public void insert(com.personal.investment.market.domain.Instrument instrument, String futureContractId,
          String optionContractId) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static class CashOnlyPortfolio implements PortfolioOverviewPort {
    @Override public List<PortfolioAccountBalance> findAccountBalances(String ownerUserId, LocalDate asOf) {
      return List.of(new PortfolioAccountBalance("01K8D43J4YFN7X9R2B6C8M0V3Q", CurrencyCode.CNY, 12_345L, 0L));
    }
    @Override public List<PortfolioOpenPosition> findOpenPositions(String ownerUserId, LocalDate asOf) { return List.of(); }
    @Override public List<PortfolioManualValuation> findManualValuations(String ownerUserId, LocalDate asOf) { return List.of(); }
    @Override public long currentLedgerVersion(String ownerUserId) { return 7L; }
  }

  private static final class UnvaluedPositionPortfolio extends CashOnlyPortfolio {
    @Override public List<PortfolioOpenPosition> findOpenPositions(String ownerUserId, LocalDate asOf) {
      return List.of(new PortfolioOpenPosition("01K8D43J4YFN7X9R2B6C8M0V3Q", "01K8D43J4YFN7X9R2B6C8M0V3R",
          CurrencyCode.CNY, java.math.BigDecimal.ONE));
    }
  }

  private static final class InMemoryHistory implements PortfolioHistorySnapshotPort {
    private final List<PortfolioHistoryPoint> points = new ArrayList<>();
    @Override public boolean exists(String ownerUserId, CurrencyCode currency, LocalDate asOfDate, long sourceLedgerVersion) {
      return false;
    }
    @Override public void append(String ownerUserId, PortfolioHistoryPoint point) { points.add(point); }
    @Override public List<PortfolioHistoryPoint> list(String ownerUserId, CurrencyCode currency, LocalDate fromInclusive,
        LocalDate toInclusive, int limit) { return List.copyOf(points); }
    @Override public List<String> ownersWithLedger() { return List.of(OWNER); }
  }
}
