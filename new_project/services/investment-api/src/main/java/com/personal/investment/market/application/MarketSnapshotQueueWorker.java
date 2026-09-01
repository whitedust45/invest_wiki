package com.personal.investment.market.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Decouples HTTPS submission from external-provider work; this worker only consumes already persisted facts. */
@Component
public class MarketSnapshotQueueWorker {
  private final MarketSnapshotService service;

  public MarketSnapshotQueueWorker(MarketSnapshotService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${app.market.snapshot-worker-fixed-delay:PT5S}")
  public void processQueuedSubmissions() {
    service.processQueuedSubmissions();
  }
}
