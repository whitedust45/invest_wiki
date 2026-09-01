package com.personal.investment.ledger.application;

/** Records that a private owner-scoped export was generated; the file bytes are never put in an audit row. */
public interface LedgerExportAuditPort {
  void recordGenerated(String ownerUserId, String exportId, LedgerExportFormat format, String contentSha256Hex,
                       long byteSize, long sourceLedgerVersion);

  static LedgerExportAuditPort noop() {
    return (ownerUserId, exportId, format, contentSha256Hex, byteSize, sourceLedgerVersion) -> { };
  }
}
