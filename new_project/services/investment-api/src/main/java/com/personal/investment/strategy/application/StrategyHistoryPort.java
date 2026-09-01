package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;
import java.util.List;

/** Read-only append history; records returned here are never an editable workspace projection. */
public interface StrategyHistoryPort {
  List<StrategyRuleVersion> findRuleVersions(String ownerUserId, StrategyKey strategyKey,
      StrategyHistoryCursor before, int limit);

  List<StrategyReferenceNav> findReferenceNavs(String ownerUserId, StrategyKey strategyKey,
      StrategyHistoryCursor before, int limit);

  List<StrategyEvaluation> findEvaluations(String ownerUserId, StrategyKey strategyKey,
      StrategyHistoryCursor before, int limit);
}
