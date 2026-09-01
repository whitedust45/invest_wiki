package com.personal.investment.strategy.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;

public record StrategyCard(StrategyKey strategyKey, String displayName, CurrencyCode currency,
                           String activeRuleVersionId, Instant inputAt, StrategyEvaluationStatus status,
                           String message) {
}
