package com.personal.investment.platform.application;

/** Acknowledges that a successfully uploaded object is eligible for server-side scanning. */
public record FileScanRequestResult(String importExportFileId, ImportExportFileStatus status) {
  public FileScanRequestResult {
    if (importExportFileId == null || importExportFileId.isBlank() || status != ImportExportFileStatus.QUARANTINED) {
      throw new IllegalArgumentException("a scan request must queue one quarantined import/export file");
    }
  }
}
