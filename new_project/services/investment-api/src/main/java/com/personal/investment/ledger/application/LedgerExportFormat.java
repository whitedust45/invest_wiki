package com.personal.investment.ledger.application;

/** Portable read-only representations of an owner's immutable ledger facts. */
public enum LedgerExportFormat {
  JSON("application/json", "json"),
  CSV("text/csv", "csv");

  private final String mediaType;
  private final String extension;

  LedgerExportFormat(String mediaType, String extension) {
    this.mediaType = mediaType;
    this.extension = extension;
  }

  public String mediaType() {
    return mediaType;
  }

  public String extension() {
    return extension;
  }
}
