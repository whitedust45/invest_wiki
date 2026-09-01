package com.personal.investment.strategy.application;

import java.util.List;

public record StrategyHistoryPage<T>(List<T> items, String nextCursor) {
  public StrategyHistoryPage {
    items = List.copyOf(items);
  }
}
