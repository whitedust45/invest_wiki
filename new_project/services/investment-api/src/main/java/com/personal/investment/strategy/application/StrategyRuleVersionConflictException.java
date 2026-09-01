package com.personal.investment.strategy.application;

public class StrategyRuleVersionConflictException extends RuntimeException {
  public StrategyRuleVersionConflictException() {
    super("active strategy rule changed");
  }
}
