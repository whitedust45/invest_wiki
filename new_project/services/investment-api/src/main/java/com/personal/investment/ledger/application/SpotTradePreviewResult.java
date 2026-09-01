package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.util.List;
import java.util.Objects;

public record SpotTradePreviewResult(CurrencyCode currency, List<PreviewPosting> postings,
                                     List<String> accountProvisioning, long allocatedCostCent) {
  public SpotTradePreviewResult {
    Objects.requireNonNull(currency, "currency must not be null");
    postings = List.copyOf(postings);
    accountProvisioning = List.copyOf(accountProvisioning);
    if (postings.size() < 2 || allocatedCostCent < 0) {
      throw new IllegalArgumentException("spot preview is invalid");
    }
  }
}
