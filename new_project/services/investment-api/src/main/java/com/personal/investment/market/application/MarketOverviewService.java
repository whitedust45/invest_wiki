package com.personal.investment.market.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketOverviewService {
  private static final int SOURCE_EVENT_LIMIT = 30;
  private final MarketOverviewPort overviewPort;

  public MarketOverviewService(MarketOverviewPort overviewPort) {
    this.overviewPort = overviewPort;
  }

  @Transactional(readOnly = true)
  public MarketOverview overview() {
    return overviewPort.latestRun().map(run -> new MarketOverview(withAttempts(run),
        overviewPort.currentQuotes(run.marketSyncRunId()), overviewPort.currentMetrics(run.marketSyncRunId()),
        overviewPort.currentBasis(run.marketSyncRunId()),
        overviewPort.recentSourceEvents(run.marketSyncRunId(), SOURCE_EVENT_LIMIT)))
        .orElseGet(() -> new MarketOverview(null, List.of(), List.of(), List.of(), List.of()));
  }

  private MarketOverview.MarketRun withAttempts(MarketOverview.MarketRun run) {
    return new MarketOverview.MarketRun(run.marketSyncRunId(), run.tradingDate(), run.runType(), run.status(),
        run.triggeredBy(), run.startedAt(), run.completedAt(), overviewPort.attempts(run.marketSyncRunId()));
  }
}
