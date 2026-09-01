package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;

public record StrategyActiveRule(String strategyActiveRuleId, String ownerUserId, StrategyKey strategyKey,
                                 String strategyRuleVersionId, long bindingVersion) {
}
