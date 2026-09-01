package com.personal.investment.ledger.application;

public class AccountDisableRejectedException extends IllegalStateException {
  public AccountDisableRejectedException(String reason) {
    super(reason);
  }
}
