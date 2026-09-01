package com.personal.investment.ledger.application;

import java.time.LocalDate;

public record CorporateActionDetail(String corporateActionId, String transactionId, String instrumentId,
                                    CorporateActionType actionType, LocalDate effectiveOn, long ratioNumerator,
                                    long ratioDenominator) {
  public CorporateActionDetail {
    if (corporateActionId == null || corporateActionId.isBlank() || transactionId == null || transactionId.isBlank()
        || instrumentId == null || instrumentId.isBlank() || effectiveOn == null || actionType == null
        || ratioNumerator <= 0 || ratioDenominator <= 0) {
      throw new IllegalArgumentException("corporate action detail is invalid");
    }
  }
}
