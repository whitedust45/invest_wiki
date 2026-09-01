package com.personal.investment.platform.application;

import java.time.Instant;
import java.util.Map;

public record UploadRequestResult(String importExportFileId, String uploadUrl, String method, String fileField,
                                  Map<String, String> formData, Instant expiresAt) {
  public UploadRequestResult {
    formData = Map.copyOf(formData);
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt must not be null");
    }
  }
}
