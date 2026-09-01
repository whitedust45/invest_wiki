package com.personal.investment.market.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketOverviewServiceTest {
  @Test
  void returnsAnExplicitEmptyWorkspaceWhenNoMarketRunExists() {
    MarketOverviewService service = new MarketOverviewService(new EmptyPort());

    MarketOverview overview = service.overview();

    assertThat(overview.latestRun()).isNull();
    assertThat(overview.quotes()).isEmpty();
    assertThat(overview.metrics()).isEmpty();
    assertThat(overview.basis()).isEmpty();
    assertThat(overview.sourceEvents()).isEmpty();
  }

  private static final class EmptyPort implements MarketOverviewPort {
    @Override public Optional<MarketOverview.MarketRun> latestRun() { return Optional.empty(); }
    @Override public List<MarketOverview.MarketAttempt> attempts(String marketSyncRunId) { return List.of(); }
    @Override public List<MarketOverview.MarketQuote> currentQuotes(String marketSyncRunId) { return List.of(); }
    @Override public List<MarketOverview.MarketMetric> currentMetrics(String marketSyncRunId) { return List.of(); }
    @Override public List<MarketOverview.MarketBasis> currentBasis(String marketSyncRunId) { return List.of(); }
    @Override public List<MarketOverview.MarketSourceEvent> recentSourceEvents(String marketSyncRunId, int limit) {
      return List.of();
    }
  }
}
