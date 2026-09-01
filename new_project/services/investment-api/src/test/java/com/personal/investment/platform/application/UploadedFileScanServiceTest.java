package com.personal.investment.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UploadedFileScanServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String FILE = "01K8D43J4YFN7X9R2B6C8M0V31";

  @Test
  void verifiesTheSignedOwnerFileHashMimeAndSizeBeforeMovingToPrivateEvidence() throws Exception {
    byte[] content = "{\"ledger\":{\"entries\":[]}}".getBytes(StandardCharsets.UTF_8);
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    ImportExportFile file = new ImportExportFile(FILE, OWNER, ImportExportFileDirection.IMPORT,
        "quarantine/" + OWNER + "/" + FILE, hash, "application/json", content.length,
        ImportExportFileStatus.UPLOAD_PENDING, "local-minio-static-v1", Instant.parse("2026-08-21T00:15:00Z"));
    CapturingFiles files = new CapturingFiles(file);
    CapturingStorage storage = new CapturingStorage(content, file);
    UploadedFileScanService service = new UploadedFileScanService(files, storage, ContentSafetyScanner.localStructural());

    ScannedUpload result = service.scan(OWNER, FILE);

    assertThat(result.status()).isEqualTo(ImportExportFileStatus.SCANNED);
    assertThat(result.evidenceObjectKey()).isEqualTo("evidence/" + OWNER + "/" + FILE + "/" + hash);
    assertThat(files.transitions).containsExactly(
        new Transition(ImportExportFileStatus.UPLOAD_PENDING, ImportExportFileStatus.QUARANTINED),
        new Transition(ImportExportFileStatus.QUARANTINED, ImportExportFileStatus.SCANNED));
    assertThat(storage.copiedTo).containsExactly(result.evidenceObjectKey());
    assertThat(storage.deleted).containsExactly(file.objectKey());
  }

  @Test
  void rejectsTamperedMetadataBeforeCopyingAnyObject() {
    byte[] content = "{}".getBytes(StandardCharsets.UTF_8);
    ImportExportFile file = new ImportExportFile(FILE, OWNER, ImportExportFileDirection.IMPORT,
        "quarantine/" + OWNER + "/" + FILE, "a".repeat(64), "application/json", content.length,
        ImportExportFileStatus.UPLOAD_PENDING, "local-minio-static-v1", Instant.parse("2026-08-21T00:15:00Z"));
    CapturingFiles files = new CapturingFiles(file);
    CapturingStorage storage = new CapturingStorage(content, file);
    storage.metadata = Map.of("x-amz-meta-owner-user-id", OWNER, "x-amz-meta-import-export-file-id", FILE,
        "x-amz-meta-content-sha256", "b".repeat(64));
    UploadedFileScanService service = new UploadedFileScanService(files, storage, ContentSafetyScanner.localStructural());

    assertThatThrownBy(() -> service.scan(OWNER, FILE)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("hash");
    assertThat(storage.copiedTo).isEmpty();
    assertThat(storage.deleted).containsExactly("evidence/" + OWNER + "/" + FILE + "/" + "a".repeat(64));
    assertThat(files.transitions).containsExactly(
        new Transition(ImportExportFileStatus.UPLOAD_PENDING, ImportExportFileStatus.QUARANTINED),
        new Transition(ImportExportFileStatus.QUARANTINED, ImportExportFileStatus.FAILED));
  }

  @Test
  void cleansTheEvidenceCopyWhenQuarantineDeletionFails() throws Exception {
    byte[] content = "{\"ledger\":{\"entries\":[]}}".getBytes(StandardCharsets.UTF_8);
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    ImportExportFile file = new ImportExportFile(FILE, OWNER, ImportExportFileDirection.IMPORT,
        "quarantine/" + OWNER + "/" + FILE, hash, "application/json", content.length,
        ImportExportFileStatus.UPLOAD_PENDING, "local-minio-static-v1", Instant.parse("2026-08-21T00:15:00Z"));
    CapturingFiles files = new CapturingFiles(file);
    CapturingStorage storage = new CapturingStorage(content, file);
    storage.failQuarantineDelete = true;
    UploadedFileScanService service = new UploadedFileScanService(files, storage, ContentSafetyScanner.localStructural());

    assertThatThrownBy(() -> service.scan(OWNER, FILE)).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("quarantine");
    assertThat(storage.deleted).containsExactly(file.objectKey(), "evidence/" + OWNER + "/" + FILE + "/" + hash);
    assertThat(files.transitions).containsExactly(
        new Transition(ImportExportFileStatus.UPLOAD_PENDING, ImportExportFileStatus.QUARANTINED),
        new Transition(ImportExportFileStatus.QUARANTINED, ImportExportFileStatus.FAILED));
  }

  private record Transition(ImportExportFileStatus from, ImportExportFileStatus to) {
  }

  private static final class CapturingFiles implements ImportExportFilePort {
    private final ImportExportFile initialFile;
    private ImportExportFileStatus status;
    private final List<Transition> transitions = new ArrayList<>();

    private CapturingFiles(ImportExportFile file) {
      this.initialFile = file;
      this.status = file.status();
    }

    @Override
    public void append(ImportExportFile ignored) {
    }

    @Override
    public Optional<ImportExportFile> findOwned(String ownerUserId, String importExportFileId) {
      return OWNER.equals(ownerUserId) && FILE.equals(importExportFileId)
          ? Optional.of(new ImportExportFile(initialFile.importExportFileId(), initialFile.ownerUserId(),
              initialFile.direction(), initialFile.objectKey(), initialFile.contentSha256Hex(), initialFile.mediaType(),
              initialFile.byteSize(), status, initialFile.encryptionKeyVersion(), initialFile.expiresAt()))
          : Optional.empty();
    }

    @Override
    public void transition(String ownerUserId, String importExportFileId, ImportExportFileStatus from,
        ImportExportFileStatus to) {
      transitions.add(new Transition(from, to));
      status = to;
    }
  }

  private static final class CapturingStorage implements UploadedObjectStoragePort {
    private final byte[] content;
    private final ImportExportFile file;
    private Map<String, String> metadata;
    private boolean failQuarantineDelete;
    private final List<String> copiedTo = new ArrayList<>();
    private final List<String> deleted = new ArrayList<>();

    private CapturingStorage(byte[] content, ImportExportFile file) {
      this.content = content;
      this.file = file;
      this.metadata = Map.of("x-amz-meta-owner-user-id", OWNER, "x-amz-meta-import-export-file-id", FILE,
          "x-amz-meta-content-sha256", file.contentSha256Hex());
    }

    @Override
    public UploadedObject read(String objectKey) {
      return new UploadedObject(content, file.mediaType(), metadata);
    }

    @Override
    public void copy(String sourceObjectKey, String destinationObjectKey) {
      copiedTo.add(destinationObjectKey);
    }

    @Override
    public void delete(String objectKey) {
      deleted.add(objectKey);
      if (failQuarantineDelete && objectKey.equals(file.objectKey())) {
        throw new IllegalStateException("quarantine delete failed");
      }
    }
  }
}
