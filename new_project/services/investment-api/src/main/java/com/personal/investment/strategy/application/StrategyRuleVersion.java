package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;
import java.util.Arrays;

public record StrategyRuleVersion(String strategyRuleVersionId, String ownerUserId, StrategyKey strategyKey,
                                  String ruleVersion, byte[] ruleHash, String ruleJson,
                                  StrategyRuleStatus status, String createdByUserId, Instant createdAt) {
  public StrategyRuleVersion {
    ruleHash = Arrays.copyOf(ruleHash, ruleHash.length);
  }

  @Override
  public byte[] ruleHash() {
    return Arrays.copyOf(ruleHash, ruleHash.length);
  }
}
