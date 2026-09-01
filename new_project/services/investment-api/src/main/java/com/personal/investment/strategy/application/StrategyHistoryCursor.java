package com.personal.investment.strategy.application;

import java.time.Instant;

/** Stable descending-page boundary: timestamp first, semantic business ID second. */
public record StrategyHistoryCursor(Instant timestamp, String itemId) {
  public StrategyHistoryCursor {
    if (timestamp == null || itemId == null || !itemId.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
      throw new IllegalArgumentException("strategy history cursor is invalid");
    }
  }
}
