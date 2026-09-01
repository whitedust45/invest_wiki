package com.personal.investment.ledger.domain;

public class InsufficientPositionException extends IllegalStateException {
  public InsufficientPositionException() {
    super("INSUFFICIENT_POSITION: available FIFO lots cannot cover the requested quantity");
  }
}
