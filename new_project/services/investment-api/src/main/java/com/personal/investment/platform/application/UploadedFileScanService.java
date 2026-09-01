package com.personal.investment.platform.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/** Server-side second verification of an uploaded file before any import parser may access it. */
@Service
public class UploadedFileScanService {
  private static final String OWNER_METADATA = "x-amz-meta-owner-user-id";
  private static final String FILE_METADATA = "x-amz-meta-import-export-file-id";
  private static final String SHA256_METADATA = "x-amz-meta-content-sha256";

  private final ImportExportFilePort filePort;
  private final UploadedObjectStoragePort storagePort;
  private final ContentSafetyScanner safetyScanner;

  public UploadedFileScanService(ImportExportFilePort filePort, UploadedObjectStoragePort storagePort,
      ContentSafetyScanner safetyScanner) {
    this.filePort = filePort;
    this.storagePort = storagePort;
    this.safetyScanner = safetyScanner;
  }

  public ScannedUpload scan(String ownerUserId, String importExportFileId) {
    ImportExportFile file = filePort.findOwned(ownerUserId, importExportFileId)
        .orElseThrow(() -> new IllegalArgumentException("import/export file was not found"));
    if (file.status() != ImportExportFileStatus.UPLOAD_PENDING) {
      throw new IllegalArgumentException("only UPLOAD_PENDING files may be scanned");
    }
    filePort.transition(ownerUserId, importExportFileId, ImportExportFileStatus.UPLOAD_PENDING,
        ImportExportFileStatus.QUARANTINED);
    return scanQueued(ownerUserId, importExportFileId);
  }

  /** Executes only after a scan request has placed the file in the worker-owned quarantine state. */
  public ScannedUpload scanQueued(String ownerUserId, String importExportFileId) {
    ImportExportFile file = filePort.findOwned(ownerUserId, importExportFileId)
        .orElseThrow(() -> new IllegalArgumentException("import/export file was not found"));
    if (file.status() != ImportExportFileStatus.QUARANTINED) {
      throw new IllegalArgumentException("only QUARANTINED files may be processed by the scan worker");
    }
    // Deterministic before reading the source: this also cleans a copy left by a crash before DB completion.
    String evidenceKey = evidenceObjectKey(file);
    try {
      UploadedObject object = storagePort.read(file.objectKey());
      validate(file, object);
      safetyScanner.scan(object);
      storagePort.copy(file.objectKey(), evidenceKey);
      storagePort.delete(file.objectKey());
      filePort.completeScan(ownerUserId, importExportFileId, evidenceKey);
      return new ScannedUpload(file.importExportFileId(), ImportExportFileStatus.SCANNED, evidenceKey);
    } catch (RuntimeException exception) {
      try {
        storagePort.delete(evidenceKey);
      } catch (RuntimeException cleanupFailure) {
        exception.addSuppressed(cleanupFailure);
      }
      try {
        filePort.transition(ownerUserId, importExportFileId, ImportExportFileStatus.QUARANTINED,
            ImportExportFileStatus.FAILED);
      } catch (RuntimeException transitionFailure) {
        exception.addSuppressed(transitionFailure);
      }
      throw exception;
    }
  }

  private static void validate(ImportExportFile file, UploadedObject object) {
    if (object.content().length != file.byteSize()) {
      throw new IllegalArgumentException("uploaded object byte size does not match its signed request");
    }
    if (!file.mediaType().equalsIgnoreCase(object.mediaType())) {
      throw new IllegalArgumentException("uploaded object media type does not match its signed request");
    }
    if (!file.ownerUserId().equals(object.metadataValue(OWNER_METADATA))
        || !file.importExportFileId().equals(object.metadataValue(FILE_METADATA))) {
      throw new IllegalArgumentException("uploaded object owner/file metadata does not match its signed request");
    }
    String actualHash = sha256(object.content());
    if (!file.contentSha256Hex().equals(actualHash)
        || !file.contentSha256Hex().equalsIgnoreCase(object.metadataValue(SHA256_METADATA))) {
      throw new IllegalArgumentException("uploaded object SHA-256 hash does not match its signed request");
    }
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String evidenceObjectKey(ImportExportFile file) {
    return "evidence/" + file.ownerUserId() + "/" + file.importExportFileId() + "/" + file.contentSha256Hex();
  }
}
