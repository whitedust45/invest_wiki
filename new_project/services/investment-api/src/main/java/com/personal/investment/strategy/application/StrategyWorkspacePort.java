package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;
import java.util.List;
import java.util.Optional;

/** Read port intentionally exposes workspace concepts rather than database rows. */
public interface StrategyWorkspacePort {
  Optional<StrategyActiveRule> findActiveRule(String ownerUserId, StrategyKey strategyKey);

  /** The evaluator reads the immutable rule body through the active pointer; it never reconstructs rules itself. */
  default Optional<StrategyRuleVersion> findRuleVersion(String ownerUserId, String strategyRuleVersionId) {
    return Optional.empty();
  }

  Optional<StrategyEvaluation> findLatestEvaluation(String ownerUserId, StrategyKey strategyKey);

  /** Only the scheduler needs this cross-owner read; normal workspace reads remain owner-scoped. */
  default List<StrategyActiveRule> findActiveRulesForScheduledEvaluation() {
    return List.of();
  }
}
