package com.personal.investment.platform.application;

import java.util.List;

/** Executes normalized commands in an isolated rollback-only transaction before a preview can be marked successful. */
@FunctionalInterface
public interface LegacyImportDryRunPort {
  LegacyImportDryRunResult validate(String ownerUserId, String importExportFileId, List<LegacyImportPreviewLine> lines);
}
