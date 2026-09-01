package com.personal.investment.ledger.application;

public interface LedgerSnapshotAuditPort {
  void recordGenerated(String ownerUserId, LedgerSnapshot snapshot);

  void recordRestored(String ownerUserId, String ledgerSnapshotId, int restoredAccountCount,
                      int restoredTransactionCount, long targetLedgerVersion);
}
