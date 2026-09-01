package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;

/**
 * Assembles owner-scoped strategy facts from local read models only. Implementations never request an external quote
 * and encode unavailable inputs explicitly so the calculator can return an explanatory status.
 */
public interface StrategyCalculationInputPort {
  StrategyCalculationInput load(String ownerUserId, StrategyKey strategyKey, Instant asOfAt,
                                StrategyReferenceNav referenceNav);
}
