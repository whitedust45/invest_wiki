package com.personal.investment.strategy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StrategyScanServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";

  @Test
  void queuesOnlyDistinctRequestedKeysAndDefaultsToAllFourStrategies() {
    MemoryScanPort scans = new MemoryScanPort();
    StrategyScanService service = new StrategyScanService(scans, null, new Ids(), new ObjectMapper());

    StrategyScan scan = service.request(OWNER, null);

    assertThat(scan.status()).isEqualTo(StrategyScanStatus.QUEUED);
    assertThat(scan.strategyKeys()).containsExactly(StrategyKey.HIGH_DIVIDEND, StrategyKey.QQQ_GROWTH,
        StrategyKey.IC_IM, StrategyKey.DEEP_PUT);
    assertThatThrownBy(() -> service.request(OWNER, List.of(StrategyKey.IC_IM, StrategyKey.IC_IM)))
        .isInstanceOf(StrategyValidationException.class)
        .satisfies(error -> assertThat(((StrategyValidationException) error).code())
            .isEqualTo(StrategyValidationCode.STRATEGY_INPUT_INVALID));
  }

  @Test
  void processesQueuedScanAsReadOnlyEvaluationsAndStoresPerStrategyOutcome() {
    MemoryScanPort scans = new MemoryScanPort();
    StrategyEvaluationService evaluator = evaluator();
    StrategyScanService service = new StrategyScanService(scans, evaluator, new Ids(), new ObjectMapper());
    StrategyScan scan = service.request(OWNER, List.of(StrategyKey.HIGH_DIVIDEND));

    assertThat(service.runOneQueuedScan()).isTrue();

    assertThat(scans.completedStatus).isEqualTo(StrategyScanStatus.SUCCEEDED);
    assertThat(scans.items).singleElement().satisfies(item -> {
      assertThat(item.strategyKey()).isEqualTo(StrategyKey.HIGH_DIVIDEND);
      assertThat(item.status()).isEqualTo(StrategyEvaluationStatus.IN_RANGE.name());
      assertThat(item.strategyEvaluationId()).isNotBlank();
    });
    assertThat(scans.completedResult).contains("\"attempted\":1", "\"failed\":0");
  }

  private static StrategyEvaluationService evaluator() {
    StrategyActiveRule active = new StrategyActiveRule("01K8D43J4YFN7X9R2B6C8M0V0A", OWNER,
        StrategyKey.HIGH_DIVIDEND, "01K8D43J4YFN7X9R2B6C8M0V0B", 1L);
    StrategyRuleVersion rule = new StrategyRuleVersion(active.strategyRuleVersionId(), OWNER,
        StrategyKey.HIGH_DIVIDEND, "high-v1", new byte[32],
        "{\"annual_expense_cent\":\"12000000\",\"annual_expense_currency\":\"CNY\","
            + "\"minimum_dividend_coverage_percent\":\"100\",\"cash_buffer_months\":\"6\"}",
        StrategyRuleStatus.ACTIVE, OWNER, Instant.parse("2026-07-31T00:00:00Z"));
    StrategyWorkspacePort workspace = new StrategyWorkspacePort() {
      @Override
      public Optional<StrategyActiveRule> findActiveRule(String ownerUserId, StrategyKey strategyKey) {
        return Optional.of(active);
      }

      @Override
      public Optional<StrategyRuleVersion> findRuleVersion(String ownerUserId, String strategyRuleVersionId) {
        return Optional.of(rule);
      }

      @Override
      public Optional<StrategyEvaluation> findLatestEvaluation(String ownerUserId, StrategyKey strategyKey) {
        return Optional.empty();
      }
    };
    StrategyReferenceNavPort nav = new StrategyReferenceNavPort() {
      @Override
      public void append(StrategyReferenceNav referenceNav) {
      }

      @Override
      public Optional<StrategyReferenceNav> findApplicable(String ownerUserId, StrategyKey strategyKey,
          Instant asOfAt) {
        return Optional.empty();
      }
    };
    StrategyCalculationInputPort input = (ownerUserId, strategyKey, asOfAt, referenceNav) ->
        new HighDividendCalculationInput("ledger:42|market:N/A|reference:N/A", true, false, 12_000_000L,
            12_000_000L);
    StrategyEvaluationPort evaluations = new StrategyEvaluationPort() {
      @Override
      public Optional<StrategyEvaluation> findByInput(String ownerUserId, String strategyRuleVersionId,
          byte[] inputHash) {
        return Optional.empty();
      }

      @Override
      public void appendEvaluation(StrategyEvaluation evaluation, byte[] inputHash) {
      }

      @Override
      public void appendSignal(StrategySignal signal) {
      }
    };
    return new StrategyEvaluationService(workspace, nav, input, new StrategyCalculationService(new ObjectMapper()),
        evaluations, new Ids(), new ObjectMapper());
  }

  private static final class MemoryScanPort implements StrategyScanPort {
    private StrategyScan scan;
    private final List<StrategyScanItem> items = new ArrayList<>();
    private StrategyScanStatus completedStatus;
    private String completedResult;
    private boolean claimed;

    @Override
    public void append(StrategyScan scan) {
      this.scan = scan;
    }

    @Override
    public Optional<StrategyScan> find(String ownerUserId, String strategyScanId) {
      return scan != null && scan.ownerUserId().equals(ownerUserId) && scan.strategyScanId().equals(strategyScanId)
          ? Optional.of(scan) : Optional.empty();
    }

    @Override
    public Optional<StrategyScan> findNextRunnable(Instant reclaimBefore) {
      return claimed || scan == null ? Optional.empty() : Optional.of(scan);
    }

    @Override
    public boolean claim(String strategyScanId, Instant reclaimBefore, Instant startedAt) {
      if (claimed || scan == null || !scan.strategyScanId().equals(strategyScanId)) {
        return false;
      }
      claimed = true;
      return true;
    }

    @Override
    public void appendItem(StrategyScanItem item) {
      items.add(item);
    }

    @Override
    public List<StrategyScanItem> findItems(String strategyScanId) {
      return List.copyOf(items);
    }

    @Override
    public void complete(String strategyScanId, StrategyScanStatus status, String resultJson, Instant completedAt) {
      completedStatus = status;
      completedResult = resultJson;
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
