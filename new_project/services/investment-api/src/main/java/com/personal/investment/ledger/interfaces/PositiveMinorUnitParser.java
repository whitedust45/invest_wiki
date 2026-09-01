package com.personal.investment.ledger.interfaces;

/** Parses wire-level minor-unit strings without any floating-point conversion. */
public final class PositiveMinorUnitParser {
  private PositiveMinorUnitParser() {
  }

  public static long parse(String value, String field) {
    if (value == null || !value.matches("[1-9][0-9]*")) {
      throw new IllegalArgumentException(field + " must be a positive integer string");
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(field + " exceeds long range", exception);
    }
  }
}
