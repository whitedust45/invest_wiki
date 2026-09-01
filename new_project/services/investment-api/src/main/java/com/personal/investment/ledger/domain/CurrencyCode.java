package com.personal.investment.ledger.domain;

/** First-release native currencies. Conversion is deliberately outside the ledger. */
public enum CurrencyCode {
  CNY,
  USD;

  public static CurrencyCode of(String value) {
    if (value == null || !value.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("currency must be an uppercase ISO 4217 code");
    }
    try {
      return valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("unsupported first-release currency: " + value, exception);
    }
  }
}
