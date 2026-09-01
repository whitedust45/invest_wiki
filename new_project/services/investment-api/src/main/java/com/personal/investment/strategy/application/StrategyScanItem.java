package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;

/** One append-only outcome per requested strategy; normal explanatory states are not worker failures. */
public record StrategyScanItem(String strategyScanItemId, String strategyScanId, StrategyKey strategyKey,
                               String strategyEvaluationId, String status, String failureCode,
                               String failureMessage, Instant createdAt) {
}
