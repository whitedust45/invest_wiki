package com.personal.investment.ledger.application;

import java.time.Instant;
import java.time.LocalDate;

/** Immutable metadata for one server-side ledger JSON artifact; the encrypted bytes stay in private object storage. */
public record LedgerSnapshot(String ledgerSnapshotId, String ownerUserId, LocalDate asOfDate,
                             long sourceLedgerVersion, String importExportFileId, String contentSha256Hex,
                             Instant createdAt) {
  public LedgerSnapshot {
    if (ledgerSnapshotId == null || !ledgerSnapshotId.matches("[0-9A-HJKMNP-TV-Z]{26}")
        || ownerUserId == null || !ownerUserId.matches("[0-9A-HJKMNP-TV-Z]{26}")
        || asOfDate == null || sourceLedgerVersion < 1
        || importExportFileId == null || !importExportFileId.matches("[0-9A-HJKMNP-TV-Z]{26}")
        || contentSha256Hex == null || !contentSha256Hex.matches("[a-f0-9]{64}") || createdAt == null) {
      throw new IllegalArgumentException("ledger snapshot metadata is invalid");
    }
  }
}
