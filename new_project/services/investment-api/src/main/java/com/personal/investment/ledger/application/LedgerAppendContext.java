package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerSourceType;

/** A pre-reserved ledger version used only by the atomic correction replacement flow. */
public record LedgerAppendContext(String ownerUserId, String correctionRootTransactionId, int revisionNo,
                                  long ledgerVersion, String strategyKey) {
  public LedgerAppendContext(String ownerUserId, String correctionRootTransactionId, int revisionNo,
                             long ledgerVersion) {
    this(ownerUserId, correctionRootTransactionId, revisionNo, ledgerVersion, null);
  }
  public LedgerAppendContext {
    if (ownerUserId == null || ownerUserId.isBlank() || correctionRootTransactionId == null
        || correctionRootTransactionId.isBlank() || revisionNo < 1 || ledgerVersion < 1) {
      throw new IllegalArgumentException("correction append context is invalid");
    }
  }

  public LedgerSourceType sourceType() {
    return LedgerSourceType.CORRECTION_REPLACEMENT;
  }
}
