package com.personal.investment.market.application;

import java.util.List;
import java.util.Optional;

/** Read-only persistence boundary for the market workspace. */
public interface MarketOverviewPort {
  Optional<MarketOverview.MarketRun> latestRun();

  List<MarketOverview.MarketAttempt> attempts(String marketSyncRunId);

  List<MarketOverview.MarketQuote> currentQuotes(String marketSyncRunId);

  List<MarketOverview.MarketMetric> currentMetrics(String marketSyncRunId);

  List<MarketOverview.MarketBasis> currentBasis(String marketSyncRunId);

  List<MarketOverview.MarketSourceEvent> recentSourceEvents(String marketSyncRunId, int limit);
}
