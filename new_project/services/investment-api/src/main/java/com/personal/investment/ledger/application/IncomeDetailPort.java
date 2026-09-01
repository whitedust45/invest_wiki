package com.personal.investment.ledger.application;

public interface IncomeDetailPort {
  void insert(IncomeDetail detail);

  static IncomeDetailPort noop() {
    return detail -> { };
  }
}
