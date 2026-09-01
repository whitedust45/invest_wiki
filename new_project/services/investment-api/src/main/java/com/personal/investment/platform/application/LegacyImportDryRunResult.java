package com.personal.investment.platform.application;

public record LegacyImportDryRunResult(Integer rejectedSourceRow, String message) {
  public static LegacyImportDryRunResult success() {
    return new LegacyImportDryRunResult(null, null);
  }

  public static LegacyImportDryRunResult rejected(int sourceRow, String message) {
    return new LegacyImportDryRunResult(sourceRow, message == null || message.isBlank() ? "ledger dry-run rejected" : message);
  }

  public boolean succeeded() {
    return rejectedSourceRow == null;
  }
}
