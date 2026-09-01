package com.personal.investment.portfolio.application;

import java.time.LocalDate;
import java.util.List;

/** Read boundary joining Ledger replay inputs and append-only Portfolio valuation facts. */
public interface PortfolioOverviewPort {
  List<PortfolioAccountBalance> findAccountBalances(String ownerUserId, LocalDate asOf);

  List<PortfolioOpenPosition> findOpenPositions(String ownerUserId, LocalDate asOf);

  List<PortfolioManualValuation> findManualValuations(String ownerUserId, LocalDate asOf);

  long currentLedgerVersion(String ownerUserId);
}
