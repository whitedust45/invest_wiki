package com.personal.investment.ledger.application;

/** Raised when a recovery request would violate the append-only ledger recovery boundary. */
public final class LedgerSnapshotRestoreRejectedException extends RuntimeException {
  public LedgerSnapshotRestoreRejectedException(String message) {
    super(message);
  }
}
