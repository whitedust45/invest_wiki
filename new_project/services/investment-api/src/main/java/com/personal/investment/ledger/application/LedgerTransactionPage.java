package com.personal.investment.ledger.application;

import java.util.List;

public record LedgerTransactionPage(List<LedgerTransactionSummary> items, String nextCursor) {
  public LedgerTransactionPage {
    items = List.copyOf(items);
  }
}
