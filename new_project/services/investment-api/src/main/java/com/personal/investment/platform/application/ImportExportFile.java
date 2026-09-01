package com.personal.investment.platform.application;

import java.time.Instant;
import java.util.Objects;

/** Owner-scoped metadata for one physical import, evidence or server-created snapshot object; no content is in MySQL. */
public record ImportExportFile(String importExportFileId, String ownerUserId, ImportExportFileDirection direction,
                               String objectKey, String contentSha256Hex, String mediaType, long byteSize,
                               ImportExportFileStatus status, String encryptionKeyVersion, Instant expiresAt) {
  public ImportExportFile {
    requireText(importExportFileId, "importExportFileId");
    requireText(ownerUserId, "ownerUserId");
    Objects.requireNonNull(direction, "direction must not be null");
    requireText(objectKey, "objectKey");
    if (contentSha256Hex == null || !contentSha256Hex.matches("^[a-f0-9]{64}$")) {
      throw new IllegalArgumentException("contentSha256Hex must be a lowercase SHA-256 hex value");
    }
    requireText(mediaType, "mediaType");
    if (byteSize <= 0) {
      throw new IllegalArgumentException("byteSize must be positive");
    }
    Objects.requireNonNull(status, "status must not be null");
    requireText(encryptionKeyVersion, "encryptionKeyVersion");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
