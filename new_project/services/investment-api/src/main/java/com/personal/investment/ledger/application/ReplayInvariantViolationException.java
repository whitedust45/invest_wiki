package com.personal.investment.ledger.application;

public class ReplayInvariantViolationException extends IllegalStateException {
  public ReplayInvariantViolationException(String message, Throwable cause) {
    super(message, cause);
  }
}
