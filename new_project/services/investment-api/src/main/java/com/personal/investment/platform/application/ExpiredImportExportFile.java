package com.personal.investment.platform.application;

public record ExpiredImportExportFile(String ownerUserId, String importExportFileId, String objectKey,
                                      String contentSha256Hex, ImportExportFileStatus status) {
  public ExpiredImportExportFile {
    if (ownerUserId == null || ownerUserId.isBlank() || importExportFileId == null || importExportFileId.isBlank()
        || objectKey == null || objectKey.isBlank() || contentSha256Hex == null || !contentSha256Hex.matches("^[a-f0-9]{64}$")
        || status == null || status == ImportExportFileStatus.DELETED) {
      throw new IllegalArgumentException("expired import/export file metadata is invalid");
    }
  }

  public String evidenceObjectKey() {
    return "evidence/" + ownerUserId + "/" + importExportFileId + "/" + contentSha256Hex;
  }
}
