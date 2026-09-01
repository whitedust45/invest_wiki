package com.personal.investment.strategy.application;

/** JSON remains an immutable evaluation payload; the service additionally returns the normalized status. */
public record StrategyCalculationResult(StrategyEvaluationStatus status, String resultJson, String explanation) {
}
