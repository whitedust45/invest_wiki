package com.personal.investment.strategy.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyHistoryServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final StrategyRuleVersion NEWER = version("01K8D43J4YFN7X9R2B6C8M0V0B", "v2",
      "2026-07-31T00:00:01Z");
  private static final StrategyRuleVersion OLDER = version("01K8D43J4YFN7X9R2B6C8M0V0A", "v1",
      "2026-07-31T00:00:00Z");

  @Test
  void returnsAnOpaqueStableCursorAndUsesItAsTheNextPageBoundary() {
    CapturingHistoryPort port = new CapturingHistoryPort();
    StrategyHistoryService service = new StrategyHistoryService(port);

    StrategyHistoryPage<StrategyRuleVersion> first = service.ruleVersions(OWNER, StrategyKey.QQQ_GROWTH, null, 1);
    StrategyHistoryPage<StrategyRuleVersion> second = service.ruleVersions(OWNER, StrategyKey.QQQ_GROWTH,
        first.nextCursor(), 1);

    assertThat(first.items()).containsExactly(NEWER);
    assertThat(first.nextCursor()).isNotBlank();
    assertThat(second.items()).containsExactly(OLDER);
    assertThat(second.nextCursor()).isNull();
    assertThat(port.beforeCalls).containsExactly(null,
        new StrategyHistoryCursor(NEWER.createdAt(), NEWER.strategyRuleVersionId()));
  }

  private static StrategyRuleVersion version(String id, String name, String createdAt) {
    return new StrategyRuleVersion(id, OWNER, StrategyKey.QQQ_GROWTH, name, new byte[32], "{}",
        StrategyRuleStatus.ACTIVE, OWNER, Instant.parse(createdAt));
  }

  private static final class CapturingHistoryPort implements StrategyHistoryPort {
    private final List<StrategyHistoryCursor> beforeCalls = new java.util.ArrayList<>();

    @Override
    public List<StrategyRuleVersion> findRuleVersions(String ownerUserId, StrategyKey strategyKey,
        StrategyHistoryCursor before, int limit) {
      beforeCalls.add(before);
      return before == null ? List.of(NEWER, OLDER) : List.of(OLDER);
    }

    @Override
    public List<StrategyReferenceNav> findReferenceNavs(String ownerUserId, StrategyKey strategyKey,
        StrategyHistoryCursor before, int limit) {
      return List.of();
    }

    @Override
    public List<StrategyEvaluation> findEvaluations(String ownerUserId, StrategyKey strategyKey,
        StrategyHistoryCursor before, int limit) {
      return List.of();
    }
  }
}
