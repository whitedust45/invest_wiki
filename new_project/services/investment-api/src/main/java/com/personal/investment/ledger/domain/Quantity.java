package com.personal.investment.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Non-monetary positive quantity normalized to the approved eight decimal places. */
public final class Quantity {
  private Quantity() {
  }

  public static BigDecimal of(BigDecimal value) {
    Objects.requireNonNull(value, "quantity must not be null");
    if (value.signum() <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    if (value.scale() > 8) {
      throw new IllegalArgumentException("quantity must not exceed 8 decimal places");
    }
    return value.setScale(8, RoundingMode.UNNECESSARY);
  }
}
