package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;
import java.util.List;

/** Persistent owner-scoped request to evaluate only already-stored strategy inputs. */
public record StrategyScan(String strategyScanId, String ownerUserId, List<StrategyKey> strategyKeys, Instant asOfAt,
                           StrategyScanStatus status, short attemptNo, Instant startedAt, Instant completedAt,
                           String resultJson, Instant createdAt) {
  public StrategyScan {
    strategyKeys = List.copyOf(strategyKeys);
  }
}
