package com.personal.investment.bootstrap.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Deployment credentials and immutable upload limits for the private object store. */
@Validated
@ConfigurationProperties(prefix = "app.object-storage")
public record ObjectStorageProperties(
    @NotBlank String endpoint,
    @NotBlank String uploadEndpoint,
    @NotBlank String bucket,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    @NotBlank String encryptionKeyVersion,
    @Positive long maxUploadBytes,
    @NotNull Duration uploadCredentialTtl,
    @NotNull Duration scanWorkerFixedDelay,
    @NotNull Duration scanLease,
    @Min(1) @Max(1_000) int scanBatchSize) {
  public ObjectStorageProperties {
    if (uploadCredentialTtl.isZero() || uploadCredentialTtl.isNegative()
        || scanWorkerFixedDelay.isZero() || scanWorkerFixedDelay.isNegative()
        || scanLease.isZero() || scanLease.isNegative()) {
      throw new IllegalArgumentException("object storage durations must be positive");
    }
  }
}
