package com.personal.investment.platform.application;

import java.util.Optional;

public interface LegacyImportPreviewPort {
  void append(LegacyImportPreview preview);

  Optional<LegacyImportPreview> findOwned(String ownerUserId, String jobId);

  Optional<LegacyImportPreview> lockOwned(String ownerUserId, String jobId);

  void expireUncommitted(String ownerUserId, String importExportFileId);

  void markCommitted(String ownerUserId, String jobId);
}
