package com.personal.investment.ledger.interfaces;

import com.personal.investment.ledger.application.LedgerSnapshotService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

/** Register as {@code ledgerDailySnapshot} after the daily data refresh, in Asia/Shanghai. */
@Component
public class LedgerSnapshotJob {
  private final LedgerSnapshotService snapshotService;

  public LedgerSnapshotJob(LedgerSnapshotService snapshotService) {
    this.snapshotService = snapshotService;
  }

  @XxlJob("ledgerDailySnapshot")
  public void snapshot() {
    LedgerSnapshotService.SnapshotBatchResult result = snapshotService.createForAllOwners();
    String message = "ledger snapshots=" + result.createdOrExisting() + ", failures=" + result.failures();
    XxlJobHelper.log(message);
    if (result.failures() > 0) {
      XxlJobHelper.handleFail(message);
    } else {
      XxlJobHelper.handleSuccess(message);
    }
  }
}
