package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerTransaction;
import java.util.Objects;

public record SpotTradeResult(LedgerTransaction transaction, long grossAmountCent, long allocatedCostCent,
                              long netRealizedPnlCent) {
  public SpotTradeResult {
    Objects.requireNonNull(transaction, "transaction must not be null");
    if (grossAmountCent <= 0 || allocatedCostCent < 0) {
      throw new IllegalArgumentException("spot trade result amounts are invalid");
    }
  }
}
