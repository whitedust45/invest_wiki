package com.personal.investment.strategy.application;

public class StrategyValidationException extends IllegalArgumentException {
  private final StrategyValidationCode code;

  public StrategyValidationException(StrategyValidationCode code, String message) {
    super(message);
    this.code = code;
  }

  public StrategyValidationCode code() {
    return code;
  }
}
