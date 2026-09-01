package com.personal.investment.ledger.application;

/** The command would make a native-currency cash or margin balance negative. */
public class InsufficientBalanceException extends IllegalStateException {
  public InsufficientBalanceException(String accountId) {
    super("INSUFFICIENT_BALANCE: account balance is insufficient for " + accountId);
  }
}
