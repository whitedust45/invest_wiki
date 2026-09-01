package com.personal.investment.strategy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StrategyEvaluationServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final Instant AS_OF = Instant.parse("2026-07-31T00:00:00Z");

  @Test
  void persistsAStrategyScopeDataStaleSignalWhenAUsdReferenceNavIsMissing() {
    ActiveRulePort workspace = new ActiveRulePort(qqqActiveRule(), qqqRule());
    CapturingEvaluationPort evaluations = new CapturingEvaluationPort();
    StrategyEvaluationService service = service(workspace, input(StrategyKey.QQQ_GROWTH,
        new QqqGrowthCalculationInput("ledger:42|market:NONE|reference:NONE", true, false, false,
            0L, 0L, 0L)), evaluations);

    StrategyEvaluation evaluation = service.evaluate(OWNER, StrategyKey.QQQ_GROWTH, AS_OF);

    assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.DATA_STALE);
    assertThat(evaluation.inputVersion()).startsWith("rule:01K8D43J4YFN7X9R2B6C8M0V0B|ledger:42");
    assertThat(evaluation.resultJson()).contains("reference_nav");
    assertThat(evaluations.evaluations).containsExactly(evaluation);
    assertThat(evaluations.signals).singleElement().satisfies(signal -> {
      assertThat(signal.signalScope()).isEqualTo(StrategySignalScope.STRATEGY);
      assertThat(signal.instrumentId()).isNull();
      assertThat(signal.signalType()).isEqualTo(StrategyEvaluationStatus.DATA_STALE);
      assertThat(signal.strategyEvaluationId()).isEqualTo(evaluation.strategyEvaluationId());
    });
  }

  @Test
  void returnsBlockedWithoutInventingAnEvaluationWhenTheStrategyHasNoRule() {
    CapturingEvaluationPort evaluations = new CapturingEvaluationPort();
    StrategyEvaluationService service = service(new ActiveRulePort(null, null), input(StrategyKey.HIGH_DIVIDEND,
        new HighDividendCalculationInput("ledger:0|market:N/A|reference:N/A", false, false, 0L, 0L)), evaluations);

    StrategyEvaluation evaluation = service.evaluate(OWNER, StrategyKey.HIGH_DIVIDEND, AS_OF);

    assertThat(evaluation.strategyEvaluationId()).isNull();
    assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.BLOCKED);
    assertThat(evaluations.evaluations).isEmpty();
  }

  @Test
  void rejectsFutureEvaluationWithoutReadingOrWritingStrategyState() {
    ActiveRulePort workspace = new ActiveRulePort(qqqActiveRule(), qqqRule());
    CapturingEvaluationPort evaluations = new CapturingEvaluationPort();
    StrategyEvaluationService service = service(workspace, input(StrategyKey.QQQ_GROWTH,
        new QqqGrowthCalculationInput("ledger:42|market:NONE|reference:NONE", true, false, false,
            0L, 0L, 0L)), evaluations);

    assertThatThrownBy(() -> service.evaluate(OWNER, StrategyKey.QQQ_GROWTH, Instant.now().plusSeconds(60)))
        .isInstanceOf(StrategyValidationException.class)
        .satisfies(error -> assertThat(((StrategyValidationException) error).code())
            .isEqualTo(StrategyValidationCode.STRATEGY_INPUT_INVALID));

    assertThat(evaluations.evaluations).isEmpty();
    assertThat(evaluations.signals).isEmpty();
  }

  private static StrategyActiveRule qqqActiveRule() {
    return new StrategyActiveRule("01K8D43J4YFN7X9R2B6C8M0V0A", OWNER, StrategyKey.QQQ_GROWTH,
        "01K8D43J4YFN7X9R2B6C8M0V0B", 1L);
  }

  private static StrategyRuleVersion qqqRule() {
    return new StrategyRuleVersion("01K8D43J4YFN7X9R2B6C8M0V0B", OWNER, StrategyKey.QQQ_GROWTH, "qqq-v1",
        new byte[32], "{\"starter_percent\":\"5\",\"target_percent\":\"10\",\"upper_percent\":\"12\","
        + "\"qld_max_share_percent\":\"35\",\"moving_average_days\":\"120\"}", StrategyRuleStatus.ACTIVE,
        OWNER, AS_OF);
  }

  private static StrategyCalculationInputPort input(StrategyKey expected, StrategyCalculationInput value) {
    return (ownerUserId, strategyKey, asOfAt, referenceNav) -> {
      assertThat(ownerUserId).isEqualTo(OWNER);
      assertThat(strategyKey).isEqualTo(expected);
      assertThat(asOfAt).isEqualTo(AS_OF);
      return value;
    };
  }

  private static StrategyEvaluationService service(StrategyWorkspacePort workspace,
      StrategyCalculationInputPort input, StrategyEvaluationPort evaluations) {
    return new StrategyEvaluationService(workspace, emptyReferenceNav(), input,
        new StrategyCalculationService(new ObjectMapper()), evaluations, new Ids(), new ObjectMapper());
  }

  private static StrategyReferenceNavPort emptyReferenceNav() {
    return new StrategyReferenceNavPort() {
      @Override
      public void append(StrategyReferenceNav referenceNav) {
      }

      @Override
      public Optional<StrategyReferenceNav> findApplicable(String ownerUserId, StrategyKey strategyKey,
          Instant asOfAt) {
        return Optional.empty();
      }
    };
  }

  private static final class ActiveRulePort implements StrategyWorkspacePort {
    private final StrategyActiveRule activeRule;
    private final StrategyRuleVersion rule;

    private ActiveRulePort(StrategyActiveRule activeRule, StrategyRuleVersion rule) {
      this.activeRule = activeRule;
      this.rule = rule;
    }

    @Override
    public Optional<StrategyActiveRule> findActiveRule(String ownerUserId, StrategyKey strategyKey) {
      return Optional.ofNullable(activeRule).filter(value -> value.strategyKey() == strategyKey);
    }

    @Override
    public Optional<StrategyEvaluation> findLatestEvaluation(String ownerUserId, StrategyKey strategyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<StrategyRuleVersion> findRuleVersion(String ownerUserId, String strategyRuleVersionId) {
      return Optional.ofNullable(rule).filter(value -> value.ownerUserId().equals(ownerUserId)
          && value.strategyRuleVersionId().equals(strategyRuleVersionId));
    }
  }

  private static final class CapturingEvaluationPort implements StrategyEvaluationPort {
    private final List<StrategyEvaluation> evaluations = new ArrayList<>();
    private final List<StrategySignal> signals = new ArrayList<>();

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
      signals.add(signal);
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
