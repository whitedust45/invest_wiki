package com.personal.investment.strategy.interfaces;

import com.personal.investment.market.application.MarketSnapshotService;
import com.personal.investment.strategy.application.StrategyDailyRefreshResult;
import com.personal.investment.strategy.application.StrategyDailyRefreshService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Register this Bean-mode handler in XXL-JOB as {@code strategyDailyRefresh} with cron {@code 0 15 0 * * ?}
 * and Asia/Shanghai timezone. It completes the local market run before evaluating strategy workspaces.
 */
@Component
public class StrategyDailyRefreshJob {
  private final StrategyDailyRefreshService refreshService;
  private final MarketSnapshotService marketSnapshotService;
  private final Clock clock;

  public StrategyDailyRefreshJob(StrategyDailyRefreshService refreshService, MarketSnapshotService marketSnapshotService,
      Clock clock) {
    this.refreshService = refreshService;
    this.marketSnapshotService = marketSnapshotService;
    this.clock = clock;
  }

  @XxlJob("strategyDailyRefresh")
  public void refresh() {
    LocalDate tradingDate = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
    MarketSnapshotService.MarketRefreshResult market = marketSnapshotService.refreshForTradingDate(tradingDate);
    StrategyDailyRefreshResult result = refreshService.refreshPersistedInputs();
    String message = "market=" + market.status() + ", strategy attempted=" + result.attempted() + ", succeeded="
        + result.succeeded() + ", failures=" + result.failures().size();
    XxlJobHelper.log(message);
    if (!result.failures().isEmpty()) {
      XxlJobHelper.log("strategy daily refresh failures={}", result.failures());
    }
    if (result.attempted() > 0 && result.succeeded() == 0) {
      XxlJobHelper.handleFail(message);
    } else {
      XxlJobHelper.handleSuccess(message);
    }
  }
}
