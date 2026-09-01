package com.personal.investment.strategy.application;

import java.util.List;

/** Per-strategy outcomes are preserved so a single bad workspace never hides successful evaluations. */
public record StrategyDailyRefreshResult(int attempted, int succeeded, List<String> failures) {
  public StrategyDailyRefreshResult {
    failures = List.copyOf(failures);
  }
}
