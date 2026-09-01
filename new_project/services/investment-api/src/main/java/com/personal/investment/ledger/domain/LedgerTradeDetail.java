package com.personal.investment.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable non-cash trade fact attached to an append-only ledger transaction. */
public record LedgerTradeDetail(
    String tradeDetailId,
    int detailNo,
    String instrumentId,
    PositionEffect positionEffect,
    BigDecimal quantity,
    Long unitPriceCent,
    BigDecimal pricePoints,
    Long contractMultiplierCent,
    LocalDate deliveryDate,
    long feeCent,
    Long optionContractMultiplier) {
  public LedgerTradeDetail {
    requireText(tradeDetailId, "tradeDetailId");
    if (detailNo < 1) {
      throw new IllegalArgumentException("detailNo must be positive");
    }
    requireText(instrumentId, "instrumentId");
    Objects.requireNonNull(positionEffect, "positionEffect must not be null");
    quantity = Quantity.of(quantity);
    if (unitPriceCent != null && pricePoints != null) {
      throw new IllegalArgumentException("unitPriceCent and pricePoints are mutually exclusive");
    }
    if (unitPriceCent == null && pricePoints == null && optionContractMultiplier == null) {
      throw new IllegalArgumentException("a price is required unless this is an option expiry detail");
    }
    if (unitPriceCent != null && unitPriceCent <= 0) {
      throw new IllegalArgumentException("unitPriceCent must be positive");
    }
    if (pricePoints != null) {
      Quantity.of(pricePoints);
    }
    if (contractMultiplierCent != null && contractMultiplierCent <= 0) {
      throw new IllegalArgumentException("contractMultiplierCent must be positive");
    }
    if (feeCent < 0) {
      throw new IllegalArgumentException("feeCent must not be negative");
    }
    if (optionContractMultiplier != null && optionContractMultiplier <= 0) {
      throw new IllegalArgumentException("optionContractMultiplier must be positive");
    }
  }

  public static LedgerTradeDetail spot(String tradeDetailId, int detailNo, String instrumentId,
      PositionEffect positionEffect, BigDecimal quantity, long unitPriceCent, long feeCent) {
    if (positionEffect != PositionEffect.OPEN && positionEffect != PositionEffect.CLOSE) {
      throw new IllegalArgumentException("spot trade must OPEN or CLOSE a position");
    }
    return new LedgerTradeDetail(tradeDetailId, detailNo, instrumentId, positionEffect, quantity, unitPriceCent,
        null, null, null, feeCent, null);
  }

  /** A confirmed worthless option expiry has no sale price; the FIFO cost is recognized as option expense. */
  public static LedgerTradeDetail optionExpiry(String tradeDetailId, int detailNo, String instrumentId,
      BigDecimal quantity, long optionContractMultiplier) {
    return new LedgerTradeDetail(tradeDetailId, detailNo, instrumentId, PositionEffect.CLOSE, quantity, null,
        null, null, null, 0, optionContractMultiplier);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
