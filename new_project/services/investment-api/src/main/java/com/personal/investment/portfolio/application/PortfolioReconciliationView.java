package com.personal.investment.portfolio.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.portfolio.domain.CashDifferenceDirection;
import com.personal.investment.portfolio.domain.ReconciliationPosition;
import com.personal.investment.portfolio.domain.ReconciliationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record PortfolioReconciliationView(String reconciliationId, String cashAccountId, LocalDate reconciliationDate,
                                          String brokerCashCent, String ledgerCashCent, String cashDifferenceCent,
                                          CashDifferenceDirection cashDifferenceDirection, CurrencyCode currency,
                                          ReconciliationStatus status, long sourceLedgerVersion, LocalDateTime createdAt,
                                          List<ReconciliationPosition> positions) {
  public PortfolioReconciliationView {
    if (reconciliationId == null || reconciliationId.isBlank() || cashAccountId == null || cashAccountId.isBlank()
        || reconciliationDate == null || brokerCashCent == null || ledgerCashCent == null || cashDifferenceCent == null
        || sourceLedgerVersion < 0 || createdAt == null) {
      throw new IllegalArgumentException("portfolio reconciliation view is invalid");
    }
    Objects.requireNonNull(cashDifferenceDirection, "cashDifferenceDirection must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    Objects.requireNonNull(status, "status must not be null");
    positions = List.copyOf(Objects.requireNonNull(positions, "positions must not be null"));
  }
}
