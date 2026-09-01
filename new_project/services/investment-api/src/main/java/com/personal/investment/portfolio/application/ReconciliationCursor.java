package com.personal.investment.portfolio.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReconciliationCursor(LocalDate reconciliationDate, long sourceLedgerVersion, LocalDateTime createdAt,
                                   String reconciliationId) {
  public ReconciliationCursor {
    if (reconciliationDate == null || sourceLedgerVersion < 0 || createdAt == null || reconciliationId == null
        || reconciliationId.isBlank()) {
      throw new IllegalArgumentException("reconciliation cursor is invalid");
    }
  }
}
