package com.personal.investment.platform.application;

import org.springframework.stereotype.Service;

@Service
public class ImportExportFileQueryService {
  private final ImportExportFilePort filePort;

  public ImportExportFileQueryService(ImportExportFilePort filePort) {
    this.filePort = filePort;
  }

  public ImportExportFileView get(String ownerUserId, String importExportFileId) {
    ImportExportFile file = filePort.findOwned(ownerUserId, importExportFileId)
        .orElseThrow(() -> new IllegalArgumentException("import/export file was not found"));
    return new ImportExportFileView(file.importExportFileId(), file.direction(), file.mediaType(), file.byteSize(),
        file.status());
  }
}
