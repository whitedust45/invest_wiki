package com.personal.investment.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileUploadRequestServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String HASH = "a".repeat(64);

  @Test
  void issuesAnOwnerBoundSingleObjectPostForEveryRequestEvenWhenTheHashRepeats() {
    CapturingFilePort files = new CapturingFilePort();
    CapturingStorage storage = new CapturingStorage();
    FileUploadRequestService service = new FileUploadRequestService(files, storage, new Ids(),
        Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC), FileUploadPolicy.localDefault());

    UploadRequestResult first = service.request(OWNER, new UploadRequestCommand(ImportExportFileDirection.IMPORT,
        "application/json", 128, HASH));
    UploadRequestResult second = service.request(OWNER, new UploadRequestCommand(ImportExportFileDirection.IMPORT,
        "application/json", 128, HASH));

    assertThat(first.importExportFileId()).isNotEqualTo(second.importExportFileId());
    assertThat(first.method()).isEqualTo("POST");
    assertThat(first.fileField()).isEqualTo("file");
    assertThat(first.expiresAt()).isEqualTo(Instant.parse("2026-08-21T00:15:00Z"));
    assertThat(files.files).hasSize(2).allSatisfy(file -> {
      assertThat(file.ownerUserId()).isEqualTo(OWNER);
      assertThat(file.status()).isEqualTo(ImportExportFileStatus.UPLOAD_PENDING);
      assertThat(file.objectKey()).startsWith("quarantine/" + OWNER + "/");
      assertThat(file.encryptionKeyVersion()).isEqualTo("local-minio-static-v1");
    });
    assertThat(storage.requests).hasSize(2).allSatisfy(request -> {
      assertThat(request.maxByteSize()).isEqualTo(10L * 1024 * 1024);
      assertThat(request.contentSha256Hex()).isEqualTo(HASH);
      assertThat(request.ownerUserId()).isEqualTo(OWNER);
      assertThat(request.importExportFileId()).startsWith("01K8D43J4YFN7X9R2B6C8M0V");
      assertThat(request.objectKey()).startsWith("quarantine/" + OWNER + "/");
    });
  }

  @Test
  void rejectsDirectionSpecificMediaTypesOversizedFilesAndMalformedHashesBeforePersisting() {
    CapturingFilePort files = new CapturingFilePort();
    FileUploadRequestService service = new FileUploadRequestService(files, new CapturingStorage(), new Ids(),
        Clock.systemUTC(), FileUploadPolicy.localDefault());

    assertThatThrownBy(() -> service.request(OWNER, new UploadRequestCommand(ImportExportFileDirection.IMPORT,
        "application/pdf", 128, HASH))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mediaType");
    assertThatThrownBy(() -> service.request(OWNER, new UploadRequestCommand(ImportExportFileDirection.RECONCILIATION_EVIDENCE,
        "image/png", 10L * 1024 * 1024 + 1, HASH))).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("byteSize");
    assertThatThrownBy(() -> service.request(OWNER, new UploadRequestCommand(ImportExportFileDirection.IMPORT,
        "application/json", 128, "not-a-sha"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sha256");
    assertThat(files.files).isEmpty();
  }

  private static final class CapturingFilePort implements ImportExportFilePort {
    private final List<ImportExportFile> files = new ArrayList<>();

    @Override
    public void append(ImportExportFile file) {
      files.add(file);
    }
  }

  private static final class CapturingStorage implements UploadObjectStoragePort {
    private final List<PresignedUploadRequest> requests = new ArrayList<>();

    @Override
    public PresignedUploadForm presignPost(PresignedUploadRequest request) {
      requests.add(request);
      return new PresignedUploadForm("https://upload.example.test/private", "file", java.util.Map.of("key", request.objectKey()));
    }
  }

  private static final class Ids implements LedgerIdGenerator {
    private int sequence;

    @Override
    public String next() {
      return "01K8D43J4YFN7X9R2B6C8M0V" + String.format("%02d", sequence++);
    }
  }
}
