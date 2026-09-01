package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.util.List;

/** Immutable, read-only result for non-trade cash transaction previews. */
public record CashTransactionPreviewResult(CurrencyCode currency, List<PreviewPosting> postings) {
  public CashTransactionPreviewResult {
    postings = List.copyOf(postings);
  }
}
