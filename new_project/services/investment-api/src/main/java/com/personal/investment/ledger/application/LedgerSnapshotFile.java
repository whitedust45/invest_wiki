package com.personal.investment.ledger.application;

/** Download-ready snapshot content after object-key, owner, status and checksum checks have passed. */
public record LedgerSnapshotFile(LedgerSnapshot snapshot, byte[] content) {
  public LedgerSnapshotFile {
    if (snapshot == null || content == null || content.length < 1) {
      throw new IllegalArgumentException("snapshot file is invalid");
    }
  }
}
