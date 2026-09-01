package com.personal.investment.platform.application;

import java.util.List;

/** Checksum input: normalized commands plus the immutable evidence and mapping context that produced them. */
public record LegacyImportPreviewPayload(String importExportFileId, String evidenceSha256Hex, String sourceSnapshotId,
    String mappingSha256Hex, List<LegacyImportPreviewLine> lines, int applicableCount, int needsReviewCount) {
  public LegacyImportPreviewPayload {
    if (importExportFileId == null || !importExportFileId.matches("[0-9A-HJKMNP-TV-Z]{26}")
        || evidenceSha256Hex == null || !evidenceSha256Hex.matches("[a-f0-9]{64}")
        || mappingSha256Hex == null || !mappingSha256Hex.matches("[a-f0-9]{64}")) {
      throw new IllegalArgumentException("legacy import preview checksum context is invalid");
    }
    lines = List.copyOf(lines == null ? List.of() : lines);
  }
}
