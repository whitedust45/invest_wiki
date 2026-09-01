package com.personal.investment.ledger.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LedgerSnapshotPort {
  void append(LedgerSnapshot snapshot);

  Optional<LedgerSnapshot> findOwned(String ownerUserId, String ledgerSnapshotId);

  Optional<LedgerSnapshot> findOwnedAtVersion(String ownerUserId, LocalDate asOfDate, long sourceLedgerVersion);

  List<LedgerSnapshot> findOwnedRecent(String ownerUserId, int limit);

  List<String> findOwnersWithLedgerFacts();
}
