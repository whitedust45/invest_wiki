package com.personal.investment.strategy.application;

import java.util.Optional;

public interface StrategyEvaluationPort {
  Optional<StrategyEvaluation> findByInput(String ownerUserId, String strategyRuleVersionId, byte[] inputHash);

  void appendEvaluation(StrategyEvaluation evaluation, byte[] inputHash);

  void appendSignal(StrategySignal signal);
}
