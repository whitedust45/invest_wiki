package com.personal.investment.strategy.domain;

public class StrategyNotFoundException extends RuntimeException {
  public StrategyNotFoundException() {
    super("strategy not found");
  }
}
