package com.personal.investment.reporting.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.time.LocalDate;
import java.util.List;

/** Persistence boundary for append-only portfolio history points. */
public interface PortfolioHistorySnapshotPort {
  boolean exists(String ownerUserId, CurrencyCode currency, LocalDate asOfDate, long sourceLedgerVersion);

  void append(String ownerUserId, PortfolioHistoryPoint point);

  List<PortfolioHistoryPoint> list(String ownerUserId, CurrencyCode currency, LocalDate fromInclusive,
                                   LocalDate toInclusive, int limit);

  List<String> ownersWithLedger();
}
