package com.personal.investment.strategy.application;

public class StrategySeedRequiresEmptyLedgerException extends RuntimeException {
  public StrategySeedRequiresEmptyLedgerException() {
    super("development strategy seed requires an empty owner ledger");
  }
}
