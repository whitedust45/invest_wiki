package com.personal.investment.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImportExportFileQueryServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String FILE = "01K8D43J4YFN7X9R2B6C8M0V31";

  @Test
  void returnsOnlySafeStatusMetadataForTheOwningUser() {
    ImportExportFileQueryService service = new ImportExportFileQueryService(new InMemoryFiles());

    ImportExportFileView result = service.get(OWNER, FILE);

    assertThat(result.importExportFileId()).isEqualTo(FILE);
    assertThat(result.direction()).isEqualTo(ImportExportFileDirection.IMPORT);
    assertThat(result.mediaType()).isEqualTo("application/json");
    assertThat(result.byteSize()).isEqualTo(2);
    assertThat(result.status()).isEqualTo(ImportExportFileStatus.SCANNED);
  }

  @Test
  void doesNotRevealAnotherOwnersFile() {
    ImportExportFileQueryService service = new ImportExportFileQueryService(new InMemoryFiles());

    assertThatThrownBy(() -> service.get("01K8D43J4YFN7X9R2B6C8M0V3Q", FILE))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
  }

  private static final class InMemoryFiles implements ImportExportFilePort {
    @Override
    public void append(ImportExportFile file) {
    }

    @Override
    public Optional<ImportExportFile> findOwned(String ownerUserId, String importExportFileId) {
      return OWNER.equals(ownerUserId) && FILE.equals(importExportFileId)
          ? Optional.of(new ImportExportFile(FILE, OWNER, ImportExportFileDirection.IMPORT,
              "evidence/" + OWNER + "/" + FILE + "/" + "a".repeat(64), "a".repeat(64), "application/json", 2,
              ImportExportFileStatus.SCANNED, "local-v1", Instant.parse("2026-08-21T00:15:00Z")))
          : Optional.empty();
    }
  }
}
