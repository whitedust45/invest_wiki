package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;

public record StrategyActiveRuleEvent(String strategyActiveRuleEventId, String ownerUserId, StrategyKey strategyKey,
                                      String previousStrategyRuleVersionId, String nextStrategyRuleVersionId,
                                      long bindingVersion, String createdByUserId, Instant createdAt) {
}
