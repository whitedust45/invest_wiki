package com.personal.investment.ledger.interfaces;

import com.personal.investment.ledger.domain.Quantity;
import java.math.BigDecimal;

public final class QuantityWireParser {
  private QuantityWireParser() {
  }

  public static BigDecimal parsePositive(String value, String field) {
    if (value == null || !value.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,8})?")) {
      throw new IllegalArgumentException(field + " must be a positive decimal string with at most 8 decimals");
    }
    try {
      return Quantity.of(new BigDecimal(value));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(field + " is invalid", exception);
    }
  }
}
