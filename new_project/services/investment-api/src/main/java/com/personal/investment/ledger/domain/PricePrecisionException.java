package com.personal.investment.ledger.domain;

public class PricePrecisionException extends IllegalArgumentException {
  public PricePrecisionException() {
    super("PRICE_PRECISION_INVALID: quantity multiplied by unit price must be an exact minor-unit integer");
  }
}
