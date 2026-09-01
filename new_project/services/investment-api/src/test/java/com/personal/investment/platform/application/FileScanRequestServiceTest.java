package com.personal.investment.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FileScanRequestServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String FILE = "01K8D43J4YFN7X9R2B6C8M0V31";

  @Test
  void queuesAnUploadedFileExactlyOnceAndKeepsAQueuedRequestIdempotent() {
    InMemoryFiles files = new InMemoryFiles(ImportExportFileStatus.UPLOAD_PENDING);
    FileScanRequestService service = new FileScanRequestService(files);

    FileScanRequestResult first = service.request(OWNER, FILE);
    FileScanRequestResult retry = service.request(OWNER, FILE);

    assertThat(first.status()).isEqualTo(ImportExportFileStatus.QUARANTINED);
    assertThat(retry.status()).isEqualTo(ImportExportFileStatus.QUARANTINED);
    assertThat(files.transitions).isEqualTo(1);
  }

  @Test
  void rejectsAFileThatWasAlreadyScannedInsteadOfReopeningIt() {
    FileScanRequestService service = new FileScanRequestService(new InMemoryFiles(ImportExportFileStatus.SCANNED));

    assertThatThrownBy(() -> service.request(OWNER, FILE)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only UPLOAD_PENDING files may request scanning");
  }

  private static final class InMemoryFiles implements ImportExportFilePort {
    private ImportExportFileStatus status;
    private int transitions;

    private InMemoryFiles(ImportExportFileStatus status) {
      this.status = status;
    }

    @Override
    public void append(ImportExportFile file) {
    }

    @Override
    public Optional<ImportExportFile> findOwned(String ownerUserId, String importExportFileId) {
      return OWNER.equals(ownerUserId) && FILE.equals(importExportFileId)
          ? Optional.of(new ImportExportFile(FILE, OWNER, ImportExportFileDirection.IMPORT,
              "quarantine/" + OWNER + "/" + FILE, "a".repeat(64), "application/json", 2,
              status, "local-minio-static-v1", Instant.parse("2026-08-21T00:15:00Z")))
          : Optional.empty();
    }

    @Override
    public void transition(String ownerUserId, String importExportFileId, ImportExportFileStatus from,
        ImportExportFileStatus to) {
      if (status != from) {
        throw new IllegalStateException("unexpected transition");
      }
      status = to;
      transitions++;
    }
  }
}
