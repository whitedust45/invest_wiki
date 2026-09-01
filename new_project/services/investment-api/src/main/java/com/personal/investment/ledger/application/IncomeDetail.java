package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.time.LocalDate;

public record IncomeDetail(String incomeDetailId, String transactionId, String incomeType, String instrumentId,
                           LocalDate entitlementDate, long grossAmountCent, long taxWithheldCent,
                           Long perShareAmountCent, CurrencyCode currency) {
  public IncomeDetail {
    if (incomeDetailId == null || incomeDetailId.isBlank() || transactionId == null || transactionId.isBlank()) {
      throw new IllegalArgumentException("income detail IDs must not be blank");
    }
    if (!"DIVIDEND".equals(incomeType) && !"INTEREST".equals(incomeType)) {
      throw new IllegalArgumentException("unsupported income type");
    }
    if (grossAmountCent <= 0 || taxWithheldCent < 0 || taxWithheldCent > grossAmountCent) {
      throw new IllegalArgumentException("income detail amounts are invalid");
    }
    if ("DIVIDEND".equals(incomeType)
        && (instrumentId == null || instrumentId.isBlank() || entitlementDate == null)) {
      throw new IllegalArgumentException("dividend requires an instrument and entitlement date");
    }
    if (perShareAmountCent != null && perShareAmountCent <= 0) {
      throw new IllegalArgumentException("per share amount must be positive");
    }
  }
}
