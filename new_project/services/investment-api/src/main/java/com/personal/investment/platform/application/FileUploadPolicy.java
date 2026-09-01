package com.personal.investment.platform.application;

import java.time.Duration;
import java.util.Objects;

/** Deployment-owned limits for one upload credential. The local defaults are part of the approved Phase 2 spec. */
public record FileUploadPolicy(long maxByteSize, Duration credentialTtl, String encryptionKeyVersion) {
  public FileUploadPolicy {
    if (maxByteSize <= 0) {
      throw new IllegalArgumentException("maxByteSize must be positive");
    }
    if (credentialTtl == null || credentialTtl.isZero() || credentialTtl.isNegative()) {
      throw new IllegalArgumentException("credentialTtl must be positive");
    }
    if (encryptionKeyVersion == null || encryptionKeyVersion.isBlank()) {
      throw new IllegalArgumentException("encryptionKeyVersion must not be blank");
    }
  }

  public static FileUploadPolicy localDefault() {
    return new FileUploadPolicy(10L * 1024 * 1024, Duration.ofMinutes(15), "local-minio-static-v1");
  }
}
