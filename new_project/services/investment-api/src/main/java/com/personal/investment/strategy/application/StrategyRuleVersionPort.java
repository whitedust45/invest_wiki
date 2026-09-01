package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;
import java.util.Optional;

public interface StrategyRuleVersionPort {
  Optional<StrategyActiveRule> lockActiveRule(String ownerUserId, StrategyKey strategyKey);

  void appendRule(StrategyRuleVersion ruleVersion);

  void createActiveRule(StrategyActiveRule activeRule);

  void replaceActiveRule(StrategyActiveRule activeRule, String expectedStrategyRuleVersionId);

  void archiveRule(String ownerUserId, String strategyRuleVersionId);

  void appendActiveRuleEvent(StrategyActiveRuleEvent event);
}
