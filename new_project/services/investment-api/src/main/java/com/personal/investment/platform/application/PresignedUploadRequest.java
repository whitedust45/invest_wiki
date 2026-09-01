package com.personal.investment.platform.application;

import java.time.Instant;

public record PresignedUploadRequest(String ownerUserId, String importExportFileId, String objectKey, String mediaType,
                                     long byteSize, long maxByteSize, String contentSha256Hex, Instant expiresAt) {
}
