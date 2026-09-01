package com.personal.investment.portfolio.domain;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Immutable manual reconciliation fact. It records differences but never creates adjustment postings. */
public record PortfolioReconciliation(String reconciliationId, String ownerUserId, String cashAccountId,
                                      LocalDate reconciliationDate, long brokerCashCent, long ledgerCashCent,
                                      long cashDifferenceCent, CashDifferenceDirection cashDifferenceDirection,
                                      CurrencyCode currency, ReconciliationStatus status, String discrepancyReason,
                                      String attachmentImportExportFileId, long sourceLedgerVersion,
                                      List<ReconciliationPosition> positions) {
  public PortfolioReconciliation {
    requireText(reconciliationId, "reconciliationId");
    requireText(ownerUserId, "ownerUserId");
    requireText(cashAccountId, "cashAccountId");
    Objects.requireNonNull(reconciliationDate, "reconciliationDate must not be null");
    if (brokerCashCent < 0 || ledgerCashCent < 0 || cashDifferenceCent < 0 || sourceLedgerVersion < 0) {
      throw new IllegalArgumentException("reconciliation monetary values and source version must not be negative");
    }
    Objects.requireNonNull(cashDifferenceDirection, "cashDifferenceDirection must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    Objects.requireNonNull(status, "status must not be null");
    positions = List.copyOf(Objects.requireNonNull(positions, "positions must not be null"));
    if (discrepancyReason != null && (discrepancyReason.isBlank() || discrepancyReason.length() > 1_000)) {
      throw new IllegalArgumentException("discrepancyReason exceeds 1000 characters");
    }
    long signedDifference = Math.subtractExact(brokerCashCent, ledgerCashCent);
    if (cashDifferenceCent != Math.abs(signedDifference)) {
      throw new IllegalArgumentException("cashDifferenceCent is inconsistent with broker and ledger cash");
    }
    CashDifferenceDirection expectedDirection = signedDifference == 0 ? CashDifferenceDirection.NONE
        : signedDifference > 0 ? CashDifferenceDirection.BROKER_GREATER : CashDifferenceDirection.LEDGER_GREATER;
    if (cashDifferenceDirection != expectedDirection) {
      throw new IllegalArgumentException("cashDifferenceDirection is inconsistent with broker and ledger cash");
    }
    boolean allPositionsMatch = positions.stream().allMatch(position -> position.quantityDifference().signum() == 0);
    if (status == ReconciliationStatus.MATCHED && (cashDifferenceCent != 0 || !allPositionsMatch || discrepancyReason != null)) {
      throw new IllegalArgumentException("MATCHED reconciliation cannot include discrepancies or a reason");
    }
    if (status == ReconciliationStatus.NEEDS_REVIEW && discrepancyReason == null) {
      throw new IllegalArgumentException("NEEDS_REVIEW reconciliation requires discrepancyReason");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
