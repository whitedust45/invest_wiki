package com.personal.investment.ledger.application;

public record LedgerSnapshotRestoreResult(String ledgerSnapshotId, int restoredAccountCount,
                                          int restoredTransactionCount, long targetLedgerVersion) {
  public LedgerSnapshotRestoreResult {
    if (ledgerSnapshotId == null || !ledgerSnapshotId.matches("[0-9A-HJKMNP-TV-Z]{26}")
        || restoredAccountCount < 1 || restoredTransactionCount < 1 || targetLedgerVersion < 1) {
      throw new IllegalArgumentException("snapshot restore result is invalid");
    }
  }
}
