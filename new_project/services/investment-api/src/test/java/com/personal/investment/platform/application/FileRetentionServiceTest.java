package com.personal.investment.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileRetentionServiceTest {
  @Test
  void deletesOnlyExpiredPhysicalObjectsThenRetainsMetadataAndAuditSummary() {
    ExpiredImportExportFile expired = new ExpiredImportExportFile("owner", "file",
        "evidence/owner/file/" + "a".repeat(64), "a".repeat(64), ImportExportFileStatus.SCANNED);
    CapturingFiles files = new CapturingFiles(expired);
    List<String> deleted = new ArrayList<>();
    List<String> audited = new ArrayList<>();
    FileRetentionService service = new FileRetentionService(files, storage(deleted),
        file -> audited.add(file.importExportFileId()));

    int count = service.sweep();

    assertThat(count).isEqualTo(1);
    assertThat(deleted).containsExactly(expired.objectKey());
    assertThat(files.marked).containsExactly(expired.importExportFileId());
    assertThat(audited).containsExactly(expired.importExportFileId());
  }

  @Test
  void alsoDeletesTheDeterministicEvidenceKeyWhenAnInterruptedScanStillPointsToQuarantine() {
    ExpiredImportExportFile interrupted = new ExpiredImportExportFile("owner", "file", "quarantine/owner/file",
        "a".repeat(64), ImportExportFileStatus.QUARANTINED);
    CapturingFiles files = new CapturingFiles(interrupted);
    List<String> deleted = new ArrayList<>();
    FileRetentionService service = new FileRetentionService(files, storage(deleted), file -> { });

    service.sweep();

    assertThat(deleted).containsExactly(interrupted.objectKey(), interrupted.evidenceObjectKey());
  }

  private static UploadedObjectStoragePort storage(List<String> deleted) {
    return new UploadedObjectStoragePort() {
      @Override
      public UploadedObject read(String objectKey) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void copy(String sourceObjectKey, String destinationObjectKey) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void delete(String objectKey) {
        deleted.add(objectKey);
      }
    };
  }

  private static final class CapturingFiles implements ImportExportFilePort {
    private final ExpiredImportExportFile expired;
    private final List<String> marked = new ArrayList<>();

    private CapturingFiles(ExpiredImportExportFile expired) {
      this.expired = expired;
    }

    @Override
    public void append(ImportExportFile file) {
    }

    @Override
    public List<ExpiredImportExportFile> findExpiredForDeletion(int limit) {
      return List.of(expired);
    }

    @Override
    public void markDeleted(ExpiredImportExportFile file) {
      marked.add(file.importExportFileId());
    }
  }
}
