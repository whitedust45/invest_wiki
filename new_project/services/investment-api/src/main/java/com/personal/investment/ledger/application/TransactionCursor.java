package com.personal.investment.ledger.application;

import java.time.LocalDate;

public record TransactionCursor(LocalDate occurredOn, String transactionId) {
  public TransactionCursor {
    if (occurredOn == null || transactionId == null || !transactionId.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
      throw new IllegalArgumentException("transaction cursor is invalid");
    }
  }
}
