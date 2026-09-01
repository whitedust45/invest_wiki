package com.personal.investment.platform.application;

import org.springframework.stereotype.Service;

/**
 * Accepts an untrusted client notification only after its POST upload completed. The notification
 * never marks a file safe: the worker re-reads and validates the object before it can be used.
 */
@Service
public class FileScanRequestService {
  private final ImportExportFilePort filePort;

  public FileScanRequestService(ImportExportFilePort filePort) {
    this.filePort = filePort;
  }

  public FileScanRequestResult request(String ownerUserId, String importExportFileId) {
    ImportExportFile file = filePort.findOwned(ownerUserId, importExportFileId)
        .orElseThrow(() -> new IllegalArgumentException("import/export file was not found"));
    if (file.status() == ImportExportFileStatus.UPLOAD_PENDING) {
      filePort.transition(ownerUserId, importExportFileId, ImportExportFileStatus.UPLOAD_PENDING,
          ImportExportFileStatus.QUARANTINED);
      return new FileScanRequestResult(importExportFileId, ImportExportFileStatus.QUARANTINED);
    }
    if (file.status() == ImportExportFileStatus.QUARANTINED) {
      return new FileScanRequestResult(importExportFileId, ImportExportFileStatus.QUARANTINED);
    }
    throw new IllegalArgumentException("only UPLOAD_PENDING files may request scanning");
  }
}
