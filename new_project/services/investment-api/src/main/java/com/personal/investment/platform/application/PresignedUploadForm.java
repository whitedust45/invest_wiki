package com.personal.investment.platform.application;

import java.util.Map;

public record PresignedUploadForm(String uploadUrl, String fileField, Map<String, String> formData) {
  public PresignedUploadForm {
    if (uploadUrl == null || uploadUrl.isBlank() || fileField == null || fileField.isBlank()) {
      throw new IllegalArgumentException("presigned upload form is invalid");
    }
    formData = Map.copyOf(formData);
  }
}
