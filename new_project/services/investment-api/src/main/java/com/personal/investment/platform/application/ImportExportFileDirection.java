package com.personal.investment.platform.application;

public enum ImportExportFileDirection {
  IMPORT,
  RECONCILIATION_EVIDENCE,
  /** Server-created, owner-only ledger snapshot artifact; never accepted from the public upload API. */
  SNAPSHOT
}
