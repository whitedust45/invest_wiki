package com.personal.investment.ledger.application;

public class CorporateActionNoOpenPositionException extends IllegalStateException {
  public CorporateActionNoOpenPositionException() {
    super("corporate action has no open spot position on its effective date");
  }
}
