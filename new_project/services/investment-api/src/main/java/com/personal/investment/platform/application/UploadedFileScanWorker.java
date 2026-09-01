package com.personal.investment.platform.application;

import com.personal.investment.bootstrap.config.ObjectStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Claims queued files with a database lease so only one application instance scans each object. */
@Component
public class UploadedFileScanWorker {
  private static final Logger LOGGER = LoggerFactory.getLogger(UploadedFileScanWorker.class);

  private final ImportExportFilePort filePort;
  private final UploadedFileScanService scanService;
  private final ObjectStorageProperties properties;

  public UploadedFileScanWorker(ImportExportFilePort filePort, UploadedFileScanService scanService,
      ObjectStorageProperties properties) {
    this.filePort = filePort;
    this.scanService = scanService;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${app.object-storage.scan-worker-fixed-delay:PT10S}")
  public void scanQueuedFiles() {
    for (ImportExportFile file : filePort.findQuarantinedForScan(properties.scanBatchSize())) {
      if (!filePort.tryClaimScan(file.ownerUserId(), file.importExportFileId(), properties.scanLease())) {
        continue;
      }
      try {
        scanService.scanQueued(file.ownerUserId(), file.importExportFileId());
      } catch (RuntimeException exception) {
        LOGGER.warn("uploaded file scan failed: ownerUserId={}, importExportFileId={}, errorType={}",
            file.ownerUserId(), file.importExportFileId(), exception.getClass().getSimpleName());
      }
    }
  }
}
