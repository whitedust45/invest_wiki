package com.personal.investment.ledger.application;

/** Private encrypted-object operations for server-owned snapshots, deliberately separate from client upload paths. */
public interface LedgerSnapshotStoragePort {
  void write(String objectKey, byte[] content, String contentSha256Hex);

  byte[] read(String objectKey);

  void delete(String objectKey);
}
