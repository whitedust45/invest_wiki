package com.personal.investment.platform.application;

import java.util.List;
import java.time.Duration;
import java.util.Optional;

public interface ImportExportFilePort {
  void append(ImportExportFile file);

  default Optional<ImportExportFile> findOwned(String ownerUserId, String importExportFileId) {
    throw new UnsupportedOperationException("owned file lookup is unavailable");
  }

  default void transition(String ownerUserId, String importExportFileId, ImportExportFileStatus from,
      ImportExportFileStatus to) {
    throw new UnsupportedOperationException("file status transition is unavailable");
  }

  default void completeScan(String ownerUserId, String importExportFileId, String evidenceObjectKey) {
    transition(ownerUserId, importExportFileId, ImportExportFileStatus.QUARANTINED, ImportExportFileStatus.SCANNED);
  }

  default List<ImportExportFile> findQuarantinedForScan(int limit) {
    throw new UnsupportedOperationException("queued file scan lookup is unavailable");
  }

  default boolean tryClaimScan(String ownerUserId, String importExportFileId, Duration lease) {
    throw new UnsupportedOperationException("file scan lease is unavailable");
  }

  default List<ExpiredImportExportFile> findExpiredForDeletion(int limit) {
    throw new UnsupportedOperationException("expired file lookup is unavailable");
  }

  default void markDeleted(ExpiredImportExportFile file) {
    throw new UnsupportedOperationException("file deletion status update is unavailable");
  }
}
