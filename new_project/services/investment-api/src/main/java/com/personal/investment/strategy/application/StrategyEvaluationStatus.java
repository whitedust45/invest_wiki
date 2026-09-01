package com.personal.investment.strategy.application;

/** Explanation-only assessment states; none is an order instruction. */
public enum StrategyEvaluationStatus {
  IN_RANGE,
  WATCH,
  BLOCKED,
  DATA_STALE,
  CROSS_CURRENCY_UNVALUED
}
