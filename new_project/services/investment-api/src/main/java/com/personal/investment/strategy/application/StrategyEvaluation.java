package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;

public record StrategyEvaluation(String strategyEvaluationId, String ownerUserId, StrategyKey strategyKey,
                                 String strategyRuleVersionId, String inputVersion, Instant asOfAt,
                                 StrategyEvaluationStatus status, String resultJson) {
}
