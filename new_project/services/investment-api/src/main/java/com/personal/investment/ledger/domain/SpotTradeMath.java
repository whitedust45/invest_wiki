package com.personal.investment.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SpotTradeMath {
  private SpotTradeMath() {
  }

  public static long grossCostCent(BigDecimal quantity, long unitPriceCent) {
    if (unitPriceCent <= 0) {
      throw new IllegalArgumentException("unitPriceCent must be positive");
    }
    BigDecimal normalizedQuantity = Quantity.of(quantity);
    try {
      return normalizedQuantity.multiply(BigDecimal.valueOf(unitPriceCent))
          .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    } catch (ArithmeticException exception) {
      throw new PricePrecisionException();
    }
  }
}
