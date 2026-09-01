package com.personal.investment.platform.application;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileUploadRequestService {
  private static final Set<String> IMPORT_MEDIA_TYPES = Set.of("application/json", "application/x-sqlite3");
  private static final Set<String> EVIDENCE_MEDIA_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

  private final ImportExportFilePort filePort;
  private final UploadObjectStoragePort objectStoragePort;
  private final LedgerIdGenerator idGenerator;
  private final Clock clock;
  private final FileUploadPolicy policy;

  public FileUploadRequestService(ImportExportFilePort filePort, UploadObjectStoragePort objectStoragePort,
      LedgerIdGenerator idGenerator, Clock clock, FileUploadPolicy policy) {
    this.filePort = filePort;
    this.objectStoragePort = objectStoragePort;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.policy = policy;
  }

  @Transactional
  public UploadRequestResult request(String ownerUserId, UploadRequestCommand command) {
    validate(ownerUserId, command);
    String fileId = idGenerator.next();
    Instant expiresAt = clock.instant().plus(policy.credentialTtl());
    String normalizedHash = command.contentSha256Hex().toLowerCase(Locale.ROOT);
    String objectKey = "quarantine/" + ownerUserId + "/" + fileId;
    PresignedUploadForm form = objectStoragePort.presignPost(new PresignedUploadRequest(ownerUserId, fileId, objectKey,
        command.mediaType(), command.byteSize(), policy.maxByteSize(), normalizedHash, expiresAt));
    filePort.append(new ImportExportFile(fileId, ownerUserId, command.direction(), objectKey, normalizedHash,
        command.mediaType(), command.byteSize(), ImportExportFileStatus.UPLOAD_PENDING, policy.encryptionKeyVersion(),
        expiresAt));
    return new UploadRequestResult(fileId, form.uploadUrl(), "POST", form.fileField(), form.formData(), expiresAt);
  }

  private void validate(String ownerUserId, UploadRequestCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank() || command == null || command.direction() == null
        || command.mediaType() == null || command.byteSize() <= 0 || command.contentSha256Hex() == null) {
      throw new IllegalArgumentException("upload request metadata is invalid");
    }
    boolean permitted = command.direction() == ImportExportFileDirection.IMPORT
        ? IMPORT_MEDIA_TYPES.contains(command.mediaType())
        : command.direction() == ImportExportFileDirection.RECONCILIATION_EVIDENCE
            && EVIDENCE_MEDIA_TYPES.contains(command.mediaType());
    if (!permitted) {
      throw new IllegalArgumentException("mediaType is not permitted for upload direction");
    }
    if (command.byteSize() > policy.maxByteSize()) {
      throw new IllegalArgumentException("byteSize exceeds configured upload limit");
    }
    if (!command.contentSha256Hex().matches("(?i)^[a-f0-9]{64}$")) {
      throw new IllegalArgumentException("sha256 must be a 64-character hex value");
    }
  }
}
