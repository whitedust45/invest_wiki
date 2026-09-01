package com.personal.investment.reporting.interfaces;

import com.personal.investment.reporting.application.PortfolioHistoryService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Register as {@code portfolioDailySnapshot} after the market refresh job in Asia/Shanghai. */
@Component
public class PortfolioDailySnapshotJob {
  private final PortfolioHistoryService historyService;
  private final Clock clock;

  public PortfolioDailySnapshotJob(PortfolioHistoryService historyService, Clock clock) {
    this.historyService = historyService;
    this.clock = clock;
  }

  @XxlJob("portfolioDailySnapshot")
  public void snapshot() {
    LocalDate date = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
    int persisted = historyService.snapshotAllOwners(date);
    XxlJobHelper.log("portfolio daily snapshots persisted={}", persisted);
    XxlJobHelper.handleSuccess("portfolio snapshots=" + persisted);
  }
}
