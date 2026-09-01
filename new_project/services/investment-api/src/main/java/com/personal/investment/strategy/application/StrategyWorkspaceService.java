package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StrategyWorkspaceService {
  private final StrategyWorkspacePort workspacePort;

  public StrategyWorkspaceService(StrategyWorkspacePort workspacePort) {
    this.workspacePort = workspacePort;
  }

  public List<StrategyCard> listCards(String ownerUserId) {
    if (ownerUserId == null || ownerUserId.isBlank()) {
      throw new IllegalArgumentException("ownerUserId is required");
    }
    return Arrays.stream(StrategyKey.values()).map(key -> card(ownerUserId, key)).toList();
  }

  public StrategyCard workspaceCard(String ownerUserId, StrategyKey strategyKey) {
    return workspace(ownerUserId, strategyKey).card();
  }

  public StrategyWorkspaceSnapshot workspace(String ownerUserId, StrategyKey strategyKey) {
    if (ownerUserId == null || ownerUserId.isBlank() || strategyKey == null) {
      throw new IllegalArgumentException("strategy workspace request is invalid");
    }
    StrategyActiveRule active = workspacePort.findActiveRule(ownerUserId, strategyKey).orElse(null);
    StrategyRuleVersion activeRule = active == null ? null
        : workspacePort.findRuleVersion(ownerUserId, active.strategyRuleVersionId()).orElse(null);
    StrategyEvaluation evaluation = workspacePort.findLatestEvaluation(ownerUserId, strategyKey).orElse(null);
    return new StrategyWorkspaceSnapshot(card(strategyKey, active, evaluation), activeRule, evaluation);
  }

  private StrategyCard card(String ownerUserId, StrategyKey key) {
    StrategyActiveRule activeRule = workspacePort.findActiveRule(ownerUserId, key).orElse(null);
    StrategyEvaluation evaluation = workspacePort.findLatestEvaluation(ownerUserId, key).orElse(null);
    return card(key, activeRule, evaluation);
  }

  private StrategyCard card(StrategyKey key, StrategyActiveRule activeRule, StrategyEvaluation evaluation) {
    if (evaluation == null) {
      return new StrategyCard(key, key.displayName(), key.currency(),
          activeRule == null ? null : activeRule.strategyRuleVersionId(), null,
          StrategyEvaluationStatus.BLOCKED, "等待基础数据");
    }
    return new StrategyCard(key, key.displayName(), key.currency(),
        activeRule == null ? null : activeRule.strategyRuleVersionId(), evaluation.asOfAt(), evaluation.status(),
        message(evaluation.status()));
  }

  private static String message(StrategyEvaluationStatus status) {
    return switch (status) {
      case IN_RANGE -> "当前处于规则区间";
      case WATCH -> "接近规则阈值";
      case BLOCKED -> "等待基础数据";
      case DATA_STALE -> "数据已过期";
      case CROSS_CURRENCY_UNVALUED -> "存在未估值的跨币种输入";
    };
  }
}
