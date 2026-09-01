package com.personal.investment.platform.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Retains immutable metadata but deletes each file's physical object 30 days after its own creation time. */
@Service
public class FileRetentionService {
  private static final int BATCH_SIZE = 100;

  private final ImportExportFilePort filePort;
  private final UploadedObjectStoragePort storagePort;
  private final FileRetentionAuditPort auditPort;

  public FileRetentionService(ImportExportFilePort filePort, UploadedObjectStoragePort storagePort,
      FileRetentionAuditPort auditPort) {
    this.filePort = filePort;
    this.storagePort = storagePort;
    this.auditPort = auditPort;
  }

  @Scheduled(fixedDelayString = "${app.object-storage.retention-sweep-interval:PT1H}")
  @Transactional
  public int sweep() {
    int deleted = 0;
    for (ExpiredImportExportFile file : filePort.findExpiredForDeletion(BATCH_SIZE)) {
      storagePort.delete(file.objectKey());
      if (!file.objectKey().equals(file.evidenceObjectKey())) {
        storagePort.delete(file.evidenceObjectKey());
      }
      filePort.markDeleted(file);
      auditPort.recordDeleted(file);
      deleted++;
    }
    return deleted;
  }
}
