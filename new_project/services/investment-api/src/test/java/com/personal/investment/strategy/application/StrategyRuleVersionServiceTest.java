package com.personal.investment.strategy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.strategy.domain.StrategyKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StrategyRuleVersionServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";

  @Test
  void createsTheFirstRuleOnlyWhenThereIsNoActiveBinding() throws Exception {
    CapturingPort port = new CapturingPort();
    StrategyRuleVersionService service = new StrategyRuleVersionService(port, new Ids());

    StrategyRuleVersion created = service.create(OWNER, StrategyKey.QQQ_GROWTH, "qqq-v1",
        new ObjectMapper().readTree("""
            {"starter_percent":"5","target_percent":"10","upper_percent":"12",
             "qld_max_share_percent":"35","moving_average_days":"120"}
            """), null);

    assertThat(created.strategyRuleVersionId()).isEqualTo("01K8D43J4YFN7X9R2B6C8M0V01");
    assertThat(port.appendedRules).containsExactly(created);
    assertThat(port.active.strategyRuleVersionId()).isEqualTo(created.strategyRuleVersionId());
    assertThat(port.events).singleElement().satisfies(event -> {
      assertThat(event.previousStrategyRuleVersionId()).isNull();
      assertThat(event.nextStrategyRuleVersionId()).isEqualTo(created.strategyRuleVersionId());
    });
  }

  @Test
  void rejectsAStaleOrMissingExpectedActiveRuleWhenReplacingARule() throws Exception {
    CapturingPort port = new CapturingPort();
    port.active = new StrategyActiveRule("01K8D43J4YFN7X9R2B6C8M0V0A", OWNER, StrategyKey.QQQ_GROWTH,
        "01K8D43J4YFN7X9R2B6C8M0V0B", 2);
    StrategyRuleVersionService service = new StrategyRuleVersionService(port, new Ids());
    var rule = new ObjectMapper().readTree("""
        {"starter_percent":"5","target_percent":"10","upper_percent":"12",
         "qld_max_share_percent":"35","moving_average_days":"120"}
        """);

    assertThatThrownBy(() -> service.create(OWNER, StrategyKey.QQQ_GROWTH, "qqq-v2", rule, null))
        .isInstanceOf(StrategyRuleVersionConflictException.class);
    assertThatThrownBy(() -> service.create(OWNER, StrategyKey.QQQ_GROWTH, "qqq-v2", rule,
        "01K8D43J4YFN7X9R2B6C8M0V0C"))
        .isInstanceOf(StrategyRuleVersionConflictException.class);
    assertThat(port.appendedRules).isEmpty();
  }

  private static final class CapturingPort implements StrategyRuleVersionPort {
    private final List<StrategyRuleVersion> appendedRules = new ArrayList<>();
    private final List<StrategyActiveRuleEvent> events = new ArrayList<>();
    private StrategyActiveRule active;

    @Override
    public Optional<StrategyActiveRule> lockActiveRule(String ownerUserId, StrategyKey strategyKey) {
      return Optional.ofNullable(active);
    }

    @Override
    public void appendRule(StrategyRuleVersion ruleVersion) {
      appendedRules.add(ruleVersion);
    }

    @Override
    public void createActiveRule(StrategyActiveRule value) {
      active = value;
    }

    @Override
    public void replaceActiveRule(StrategyActiveRule value, String expectedStrategyRuleVersionId) {
      active = value;
    }

    @Override
    public void archiveRule(String ownerUserId, String strategyRuleVersionId) {
      // The test asserts activation semantics; archival has no impact on its read model.
    }

    @Override
    public void appendActiveRuleEvent(StrategyActiveRuleEvent event) {
      events.add(event);
    }
  }

  private static final class Ids implements StrategyIdGenerator {
    private int sequence;

    @Override
    public String next() {
      return "01K8D43J4YFN7X9R2B6C8M0V" + String.format("%02d", ++sequence);
    }
  }
}
