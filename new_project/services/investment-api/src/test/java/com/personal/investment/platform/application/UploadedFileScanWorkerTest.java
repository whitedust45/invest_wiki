package com.personal.investment.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.bootstrap.config.ObjectStorageProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UploadedFileScanWorkerTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String FILE = "01K8D43J4YFN7X9R2B6C8M0V31";

  @Test
  void claimsAQueuedFileBeforeTheWorkerProcessesIt() throws Exception {
    byte[] content = "{\"ledger\":{\"entries\":[]}}".getBytes(StandardCharsets.UTF_8);
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    InMemoryFiles files = new InMemoryFiles(hash, content.length);
    UploadedFileScanService scanner = new UploadedFileScanService(files, new InMemoryStorage(content, hash),
        ContentSafetyScanner.localStructural());
    ObjectStorageProperties properties = new ObjectStorageProperties("http://127.0.0.1:9000", "http://127.0.0.1:9000",
        "private", "access", "secret", "local-v1", 10_485_760, Duration.ofMinutes(15), Duration.ofSeconds(10),
        Duration.ofMinutes(10), 20);
    UploadedFileScanWorker worker = new UploadedFileScanWorker(files, scanner, properties);

    worker.scanQueuedFiles();

    assertThat(files.claims).isEqualTo(1);
    assertThat(files.status).isEqualTo(ImportExportFileStatus.SCANNED);
  }

  private static final class InMemoryFiles implements ImportExportFilePort {
    private final String hash;
    private final long byteSize;
    private ImportExportFileStatus status = ImportExportFileStatus.QUARANTINED;
    private int claims;

    private InMemoryFiles(String hash, long byteSize) {
      this.hash = hash;
      this.byteSize = byteSize;
    }

    @Override
    public void append(ImportExportFile file) {
    }

    @Override
    public Optional<ImportExportFile> findOwned(String ownerUserId, String importExportFileId) {
      return OWNER.equals(ownerUserId) && FILE.equals(importExportFileId) ? Optional.of(file()) : Optional.empty();
    }

    @Override
    public List<ImportExportFile> findQuarantinedForScan(int limit) {
      return status == ImportExportFileStatus.QUARANTINED ? List.of(file()) : List.of();
    }

    @Override
    public boolean tryClaimScan(String ownerUserId, String importExportFileId, Duration lease) {
      claims++;
      return OWNER.equals(ownerUserId) && FILE.equals(importExportFileId) && status == ImportExportFileStatus.QUARANTINED;
    }

    @Override
    public void transition(String ownerUserId, String importExportFileId, ImportExportFileStatus from,
        ImportExportFileStatus to) {
      if (status != from) {
        throw new IllegalStateException("unexpected status transition");
      }
      status = to;
    }

    private ImportExportFile file() {
      return new ImportExportFile(FILE, OWNER, ImportExportFileDirection.IMPORT, "quarantine/" + OWNER + "/" + FILE,
          hash, "application/json", byteSize, status, "local-v1", Instant.parse("2026-08-21T00:15:00Z"));
    }
  }

  private static final class InMemoryStorage implements UploadedObjectStoragePort {
    private final byte[] content;
    private final String hash;

    private InMemoryStorage(byte[] content, String hash) {
      this.content = content;
      this.hash = hash;
    }

    @Override
    public UploadedObject read(String objectKey) {
      return new UploadedObject(content, "application/json", Map.of(
          "x-amz-meta-owner-user-id", OWNER,
          "x-amz-meta-import-export-file-id", FILE,
          "x-amz-meta-content-sha256", hash));
    }

    @Override
    public void copy(String sourceObjectKey, String destinationObjectKey) {
    }

    @Override
    public void delete(String objectKey) {
    }
  }
}
