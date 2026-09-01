package com.personal.investment.ledger.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.time.LocalDate;

@FunctionalInterface
public interface SpotHistoryReplayer {
  void rebuild(String ownerUserId, long sourceLedgerVersion);

  /** Validates a proposed corporate action without generating identifiers or writing projections. */
  default void validateCorporateAction(String ownerUserId, HistoricalCorporateAction action) {
  }

  /** Returns the exact owner-wide open quantity at a historical entitlement date. */
  default BigDecimal quantityAt(String ownerUserId, String instrumentId, LocalDate asOf) {
    throw new UnsupportedOperationException("historical quantity replay is unavailable");
  }

  /** Returns non-zero FIFO-backed stock/ETF/long-option positions for one cash account at the requested business date. */
  default List<HistoricalFifoPosition> positionsAt(String ownerUserId, String cashAccountId, LocalDate asOf) {
    throw new UnsupportedOperationException("historical FIFO position replay is unavailable");
  }

  static SpotHistoryReplayer noop() {
    return (ownerUserId, sourceLedgerVersion) -> { };
  }
}
