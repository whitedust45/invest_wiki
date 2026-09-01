package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V10ImportPreviewChecksumStorageSpecificationTest {
  @Test
  void preservesImmutablePreviewPayloadBytesAndExpiresOnlyLegacyUncommittedPreviews() throws Exception {
    String migration = Files.readString(Path.of(
        "src/main/resources/db/migration/V10__preserve_import_preview_checksum_payloads.sql"));

    assertThat(migration)
        .contains("MODIFY COLUMN mapping_json LONGTEXT NOT NULL")
        .contains("MODIFY COLUMN preview_json LONGTEXT NOT NULL")
        .contains("SET status = 'EXPIRED'")
        .contains("'PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'NEEDS_REVIEW'");
  }
}
