package com.personal.investment.strategy.application;

import java.util.List;

public record StrategySeedResult(String seedName, int createdCashAccounts, int createdInstruments,
                                 int createdTransactions, int createdEvaluations, List<String> currencies) {
  public StrategySeedResult {
    currencies = List.copyOf(currencies);
  }
}
