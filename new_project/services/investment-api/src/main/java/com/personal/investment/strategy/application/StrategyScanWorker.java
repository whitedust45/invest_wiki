package com.personal.investment.strategy.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Small persistent local queue worker; the daily XXL-JOB entry can enqueue or invoke this same application boundary. */
@Component
public class StrategyScanWorker {
  private final StrategyScanService scanService;

  public StrategyScanWorker(StrategyScanService scanService) {
    this.scanService = scanService;
  }

  @Scheduled(fixedDelayString = "${app.strategy.scan-worker-fixed-delay:PT1S}")
  public void processOneQueuedScan() {
    scanService.runOneQueuedScan();
  }
}
