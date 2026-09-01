package com.personal.investment.ledger.application;

@FunctionalInterface
public interface FuturesHistoryReplayer {
  void rebuild(String ownerUserId, long sourceLedgerVersion);

  static FuturesHistoryReplayer noop() {
    return (ownerUserId, sourceLedgerVersion) -> { };
  }
}
