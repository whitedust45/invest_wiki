package com.personal.investment.platform.application;

/** Owner-scoped status metadata; object keys, hashes, encryption details and upload credentials stay server-side. */
public record ImportExportFileView(String importExportFileId, ImportExportFileDirection direction, String mediaType,
                                   long byteSize, ImportExportFileStatus status) {
  public ImportExportFileView {
    if (importExportFileId == null || importExportFileId.isBlank() || direction == null || mediaType == null
        || mediaType.isBlank() || byteSize < 1 || status == null) {
      throw new IllegalArgumentException("import/export file view is invalid");
    }
  }
}
