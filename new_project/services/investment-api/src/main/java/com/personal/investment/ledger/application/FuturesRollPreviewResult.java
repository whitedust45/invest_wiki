package com.personal.investment.ledger.application;

/**
 * The preview group key is only a human-readable draft correlation token. The persisted operation group key is
 * allocated inside the later atomic write, so a preview can never be mistaken for an already-written ledger fact.
 */
public record FuturesRollPreviewResult(String proposedOperationGroupKey, FuturesClosePreviewResult closePreview,
                                       FuturesOpenPreviewResult openPreview) {
}
