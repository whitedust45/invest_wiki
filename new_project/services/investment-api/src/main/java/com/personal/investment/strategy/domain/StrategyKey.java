package com.personal.investment.strategy.domain;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.util.Locale;

/** The four deliberate strategy workspaces. A key is also the immutable ledger attribution value. */
public enum StrategyKey {
  HIGH_DIVIDEND(CurrencyCode.CNY, "高分红"),
  QQQ_GROWTH(CurrencyCode.USD, "QQQ"),
  IC_IM(CurrencyCode.CNY, "IC/IM"),
  DEEP_PUT(CurrencyCode.USD, "深度 Put");

  private final CurrencyCode currency;
  private final String displayName;

  StrategyKey(CurrencyCode currency, String displayName) {
    this.currency = currency;
    this.displayName = displayName;
  }

  public CurrencyCode currency() {
    return currency;
  }

  public String displayName() {
    return displayName;
  }

  public static StrategyKey from(String value) {
    if (value == null || value.isBlank()) {
      throw new StrategyNotFoundException();
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new StrategyNotFoundException();
    }
  }
}
