package com.personal.investment.portfolio.application;

import java.time.LocalDate;
import java.util.List;

public interface PortfolioReconciliationQueryPort {
  List<PortfolioReconciliationView> find(String ownerUserId, ReconciliationCursor cursor, int limit,
                                           String cashAccountId, LocalDate from, LocalDate to);
}
