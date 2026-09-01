package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalObjectStorageSpecificationTest {
  @Test
  void localStackPinsMinioWithServerSideEncryptionAndPrivateBucketInitialization() throws Exception {
    String compose = Files.readString(Path.of("../../infra/docker-compose.local.yml"));
    String environment = Files.readString(Path.of("../../.env.example"));
    String application = Files.readString(Path.of("src/main/resources/application.yml"));

    assertThat(compose)
        .contains("minio:")
        .contains("minio-init:")
        .contains("MINIO_KMS_SECRET_KEY")
        .contains("MINIO_KMS_AUTO_ENCRYPTION")
        .contains("mc mb --ignore-existing")
        .contains("mc anonymous set none");
    assertThat(environment)
        .contains("MINIO_SSE_MASTER_KEY_BASE64=")
        .contains("OBJECT_STORAGE_ENCRYPTION_KEY_VERSION=");
    assertThat(application).contains("object-storage:");
  }
}
