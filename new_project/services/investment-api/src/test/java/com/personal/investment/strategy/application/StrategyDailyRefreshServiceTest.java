package com.personal.investment.strategy.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StrategyDailyRefreshServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";

  @Test
  void evaluatesEveryActiveWorkspaceAndRetainsPerWorkspaceFailures() {
    ActiveRules workspace = new ActiveRules(List.of(
        active(OWNER, StrategyKey.HIGH_DIVIDEND), active("invalid-owner", StrategyKey.IC_IM)));
    CapturingEvaluationPort evaluations = new CapturingEvaluationPort();
    StrategyEvaluationService evaluator = new StrategyEvaluationService(workspace, new StrategyReferenceNavPort() {
      @Override
      public void append(StrategyReferenceNav referenceNav) {
      }

      @Override
      public Optional<StrategyReferenceNav> findApplicable(String ownerUserId, StrategyKey strategyKey,
          Instant asOfAt) {
        return Optional.empty();
      }
    }, (ownerUserId, strategyKey, asOfAt, referenceNav) -> new HighDividendCalculationInput(
        "ledger:42|market:N/A|reference:N/A", true, false, 12_000_000L, 12_000_000L),
        new StrategyCalculationService(new ObjectMapper()), evaluations, new Ids(), new ObjectMapper());
    StrategyDailyRefreshService service = new StrategyDailyRefreshService(workspace, evaluator,
        Clock.fixed(Instant.parse("2026-07-31T00:15:00Z"), ZoneOffset.UTC));

    StrategyDailyRefreshResult result = service.refreshPersistedInputs();

    assertThat(result.attempted()).isEqualTo(2);
    assertThat(result.succeeded()).isEqualTo(1);
    assertThat(result.failures()).containsExactly("invalid-owner:IC_IM:IllegalArgumentException");
    assertThat(evaluations.evaluations).singleElement().satisfies(value -> {
      assertThat(value.strategyKey()).isEqualTo(StrategyKey.HIGH_DIVIDEND);
      assertThat(value.asOfAt()).isEqualTo(Instant.parse("2026-07-31T00:15:00Z"));
    });
  }

  private static StrategyActiveRule active(String ownerUserId, StrategyKey strategyKey) {
    return new StrategyActiveRule("01K8D43J4YFN7X9R2B6C8M0V0A", ownerUserId, strategyKey,
        "01K8D43J4YFN7X9R2B6C8M0V0B", 1L);
  }

  private static final class ActiveRules implements StrategyWorkspacePort {
    private final List<StrategyActiveRule> rules;

    private ActiveRules(List<StrategyActiveRule> rules) {
      this.rules = rules;
    }

    @Override
    public Optional<StrategyActiveRule> findActiveRule(String ownerUserId, StrategyKey strategyKey) {
      return rules.stream().filter(value -> value.ownerUserId().equals(ownerUserId)
          && value.strategyKey() == strategyKey).findFirst();
    }

    @Override
    public Optional<StrategyEvaluation> findLatestEvaluation(String ownerUserId, StrategyKey strategyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<StrategyRuleVersion> findRuleVersion(String ownerUserId, String strategyRuleVersionId) {
      return rules.stream().filter(value -> value.ownerUserId().equals(ownerUserId))
          .findFirst().map(activeRule -> new StrategyRuleVersion(activeRule.strategyRuleVersionId(), ownerUserId,
              activeRule.strategyKey(), "test-v1", new byte[32],
              "{\"annual_expense_cent\":\"12000000\",\"annual_expense_currency\":\"CNY\","
                  + "\"minimum_dividend_coverage_percent\":\"100\",\"cash_buffer_months\":\"6\"}",
              StrategyRuleStatus.ACTIVE, ownerUserId, Instant.parse("2026-07-31T00:00:00Z")));
    }

    @Override
    public List<StrategyActiveRule> findActiveRulesForScheduledEvaluation() {
      return rules;
    }
  }

  private static final class CapturingEvaluationPort implements StrategyEvaluationPort {
    private final List<StrategyEvaluation> evaluations = new ArrayList<>();

    @Override
    public Optional<StrategyEvaluation> findByInput(String ownerUserId, String strategyRuleVersionId, byte[] inputHash) {
      return Optional.empty();
    }

    @Override
    public void appendEvaluation(StrategyEvaluation evaluation, byte[] inputHash) {
      evaluations.add(evaluation);
    }

    @Override
    public void appendSignal(StrategySignal signal) {
    }
  }

  private static final class Ids implements StrategyIdGenerator {
    private int value;

    @Override
    public String next() {
      return "01K8D43J4YFN7X9R2B6C8M0V" + String.format("%02d", ++value);
    }
  }
}
