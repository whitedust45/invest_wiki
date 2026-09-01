package com.personal.investment.portfolio.application;

import com.personal.investment.portfolio.domain.PortfolioReconciliation;
import java.time.LocalDate;

public interface PortfolioReconciliationPort {
  LedgerReconciliationSnapshot snapshot(String ownerUserId, String cashAccountId, LocalDate asOf);

  void append(PortfolioReconciliation reconciliation);

  /** Proves that an optional evidence file is owner-scoped and has the reconciliation-evidence direction. */
  void requireOwnedEvidence(String ownerUserId, String attachmentImportExportFileId);
}
