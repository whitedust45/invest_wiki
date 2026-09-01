package com.personal.investment.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Immutable broker-versus-ledger position row; zero on either side is meaningful in the complete union snapshot. */
public record ReconciliationPosition(String reconciliationPositionId, String instrumentId, BigDecimal brokerQuantity,
                                     BigDecimal ledgerQuantity, BigDecimal quantityDifference) {
  public ReconciliationPosition {
    requireId(reconciliationPositionId, "reconciliationPositionId");
    requireId(instrumentId, "instrumentId");
    brokerQuantity = normalizedNonNegative(brokerQuantity, "brokerQuantity");
    ledgerQuantity = normalizedNonNegative(ledgerQuantity, "ledgerQuantity");
    quantityDifference = normalizedSigned(quantityDifference, "quantityDifference");
    if (quantityDifference.compareTo(brokerQuantity.subtract(ledgerQuantity).setScale(8, RoundingMode.UNNECESSARY)) != 0) {
      throw new IllegalArgumentException("quantityDifference must equal brokerQuantity minus ledgerQuantity");
    }
  }

  private static BigDecimal normalizedNonNegative(BigDecimal value, String field) {
    if (value == null || value.signum() < 0 || value.scale() > 8) {
      throw new IllegalArgumentException(field + " must be nonnegative with at most 8 decimal places");
    }
    return value.setScale(8, RoundingMode.UNNECESSARY);
  }

  private static BigDecimal normalizedSigned(BigDecimal value, String field) {
    if (value == null || value.scale() > 8) {
      throw new IllegalArgumentException(field + " must have at most 8 decimal places");
    }
    return value.setScale(8, RoundingMode.UNNECESSARY);
  }

  private static void requireId(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
