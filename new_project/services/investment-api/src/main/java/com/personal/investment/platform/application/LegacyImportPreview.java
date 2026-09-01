package com.personal.investment.platform.application;

import java.time.Instant;

public record LegacyImportPreview(String importPreviewId, String ownerUserId, String jobId, String importExportFileId,
    LegacyImportFormat format, String sourceSnapshotId, String mappingJson, String previewJson,
    String previewChecksumHex, LegacyImportPreviewStatus status, Instant expiresAt, Instant createdAt) {
}
