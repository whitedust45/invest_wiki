package com.personal.investment.ledger.application;

public class AccountVersionConflictException extends IllegalStateException {
  public AccountVersionConflictException() {
    super("cash account version does not match If-Match");
  }
}
