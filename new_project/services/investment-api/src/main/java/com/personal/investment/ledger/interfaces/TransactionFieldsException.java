package com.personal.investment.ledger.interfaces;

public class TransactionFieldsException extends IllegalArgumentException {
  public TransactionFieldsException(String message) {
    super("TRANSACTION_FIELDS_INVALID: " + message);
  }
}
