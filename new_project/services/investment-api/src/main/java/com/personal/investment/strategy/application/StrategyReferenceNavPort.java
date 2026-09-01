package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;
import java.util.Optional;

public interface StrategyReferenceNavPort {
  void append(StrategyReferenceNav referenceNav);

  Optional<StrategyReferenceNav> findApplicable(String ownerUserId, StrategyKey strategyKey, Instant asOfAt);
}
