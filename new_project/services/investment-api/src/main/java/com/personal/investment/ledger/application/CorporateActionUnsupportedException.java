package com.personal.investment.ledger.application;

public class CorporateActionUnsupportedException extends IllegalArgumentException {
  public CorporateActionUnsupportedException(String message) {
    super(message);
  }
}
