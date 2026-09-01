package com.personal.investment.ledger.application;

public class CorrectionRejectedException extends IllegalStateException {
  public CorrectionRejectedException(String message) {
    super(message);
  }
}
