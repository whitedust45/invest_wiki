package com.personal.investment.market.domain;

import java.util.Locale;

/** Immutable CFFEX future metadata. The multiplier is a native-currency minor-unit amount per point. */
public record FutureSpecification(String productCode, long contractMultiplierCent) {
  public FutureSpecification {
    if (productCode == null || productCode.isBlank()) {
      throw new IllegalArgumentException("future productCode must not be blank");
    }
    productCode = productCode.trim().toUpperCase(Locale.ROOT);
    if (!("IC".equals(productCode) || "IM".equals(productCode))) {
      throw new IllegalArgumentException("future productCode must be IC or IM");
    }
    if (contractMultiplierCent <= 0) {
      throw new IllegalArgumentException("future contractMultiplierCent must be positive");
    }
  }
}
