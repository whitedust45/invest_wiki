package com.personal.investment.strategy.infrastructure;

import com.personal.investment.strategy.application.StrategyActiveRule;
import com.personal.investment.strategy.application.StrategyActiveRuleEvent;
import com.personal.investment.strategy.application.StrategyEvaluation;
import com.personal.investment.strategy.application.StrategyEvaluationStatus;
import com.personal.investment.strategy.application.StrategyRuleVersion;
import com.personal.investment.strategy.application.StrategyRuleStatus;
import com.personal.investment.strategy.application.StrategyRuleVersionConflictException;
import com.personal.investment.strategy.application.StrategyRuleVersionPort;
import com.personal.investment.strategy.application.StrategyEvaluationPort;
import com.personal.investment.strategy.application.StrategyHistoryCursor;
import com.personal.investment.strategy.application.StrategyHistoryPort;
import com.personal.investment.strategy.application.StrategyReferenceNav;
import com.personal.investment.strategy.application.StrategyReferenceNavPort;
import com.personal.investment.strategy.application.StrategySignal;
import com.personal.investment.strategy.application.StrategyWorkspacePort;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MyBatisStrategyWorkspaceAdapter implements StrategyWorkspacePort, StrategyRuleVersionPort,
    StrategyReferenceNavPort, StrategyEvaluationPort, StrategyHistoryPort {
  private final StrategyWorkspaceMapper mapper;

  public MyBatisStrategyWorkspaceAdapter(StrategyWorkspaceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<StrategyActiveRule> lockActiveRule(String ownerUserId, StrategyKey strategyKey) {
    return Optional.ofNullable(mapper.lockActiveRule(ownerUserId, strategyKey.name())).map(this::activeRule);
  }

  @Override
  public Optional<StrategyActiveRule> findActiveRule(String ownerUserId, StrategyKey strategyKey) {
    return Optional.ofNullable(mapper.findActiveRule(ownerUserId, strategyKey.name())).map(this::activeRule);
  }

  @Override
  public Optional<StrategyRuleVersion> findRuleVersion(String ownerUserId, String strategyRuleVersionId) {
    return Optional.ofNullable(mapper.findRuleVersion(ownerUserId, strategyRuleVersionId)).map(this::ruleVersion);
  }

  @Override
  public Optional<StrategyEvaluation> findLatestEvaluation(String ownerUserId, StrategyKey strategyKey) {
    return Optional.ofNullable(mapper.findLatestEvaluation(ownerUserId, strategyKey.name())).map(row ->
        new StrategyEvaluation(row.strategyEvaluationId(), row.ownerUserId(), StrategyKey.from(row.strategyKey()),
            row.strategyRuleVersionId(), row.inputVersion(), row.asOfAt(),
            StrategyEvaluationStatus.valueOf(row.status()), row.resultJson()));
  }

  @Override
  public List<StrategyActiveRule> findActiveRulesForScheduledEvaluation() {
    return mapper.findAllActiveRules().stream().map(this::activeRule).toList();
  }

  @Override
  public List<StrategyRuleVersion> findRuleVersions(String ownerUserId, StrategyKey strategyKey,
      StrategyHistoryCursor before, int limit) {
    return mapper.findRuleVersions(ownerUserId, strategyKey.name(), timestamp(before), itemId(before), limit).stream()
        .map(this::ruleVersion).toList();
  }

  @Override
  public List<StrategyReferenceNav> findReferenceNavs(String ownerUserId, StrategyKey strategyKey,
      StrategyHistoryCursor before, int limit) {
    return mapper.findReferenceNavs(ownerUserId, strategyKey.name(), timestamp(before), itemId(before), limit).stream()
        .map(row -> new StrategyReferenceNav(row.strategyReferenceNavId(), row.ownerUserId(),
            StrategyKey.from(row.strategyKey()), CurrencyCode.of(row.currency()), row.referenceNavCent(), row.asOfAt(),
            row.validUntil(), row.source(), row.createdAt())).toList();
  }

  @Override
  public List<StrategyEvaluation> findEvaluations(String ownerUserId, StrategyKey strategyKey,
      StrategyHistoryCursor before, int limit) {
    return mapper.findEvaluations(ownerUserId, strategyKey.name(), timestamp(before), itemId(before), limit).stream()
        .map(this::evaluation).toList();
  }

  @Override
  public void appendRule(StrategyRuleVersion ruleVersion) {
    if (mapper.insertRule(new StrategyWorkspaceMapper.RuleVersionRow(ruleVersion.strategyRuleVersionId(),
        ruleVersion.ownerUserId(), ruleVersion.strategyKey().name(), ruleVersion.ruleVersion(), ruleVersion.ruleHash(),
        ruleVersion.ruleJson(), ruleVersion.status().name(), ruleVersion.createdByUserId(), ruleVersion.createdAt())) != 1) {
      throw new IllegalStateException("strategy rule was not appended");
    }
  }

  @Override
  public void createActiveRule(StrategyActiveRule activeRule) {
    if (mapper.insertActiveRule(new StrategyWorkspaceMapper.ActiveRuleRow(activeRule.strategyActiveRuleId(),
        activeRule.ownerUserId(), activeRule.strategyKey().name(), activeRule.strategyRuleVersionId(),
        activeRule.bindingVersion())) != 1) {
      throw new StrategyRuleVersionConflictException();
    }
  }

  @Override
  public void replaceActiveRule(StrategyActiveRule activeRule, String expectedStrategyRuleVersionId) {
    if (mapper.replaceActiveRule(activeRule.ownerUserId(), activeRule.strategyKey().name(),
        expectedStrategyRuleVersionId, activeRule.strategyRuleVersionId(), activeRule.bindingVersion()) != 1) {
      throw new StrategyRuleVersionConflictException();
    }
  }

  @Override
  public void archiveRule(String ownerUserId, String strategyRuleVersionId) {
    if (mapper.archiveRule(ownerUserId, strategyRuleVersionId) != 1) {
      throw new StrategyRuleVersionConflictException();
    }
  }

  @Override
  public void appendActiveRuleEvent(StrategyActiveRuleEvent event) {
    if (mapper.insertActiveRuleEvent(new StrategyWorkspaceMapper.ActiveRuleEventRow(event.strategyActiveRuleEventId(),
        event.ownerUserId(), event.strategyKey().name(), event.previousStrategyRuleVersionId(),
        event.nextStrategyRuleVersionId(), event.bindingVersion(), event.createdByUserId(), event.createdAt())) != 1) {
      throw new IllegalStateException("strategy active rule event was not appended");
    }
  }

  @Override
  public void append(StrategyReferenceNav referenceNav) {
    if (mapper.insertReferenceNav(new StrategyWorkspaceMapper.ReferenceNavRow(referenceNav.strategyReferenceNavId(),
        referenceNav.ownerUserId(), referenceNav.strategyKey().name(), referenceNav.currency().name(),
        referenceNav.referenceNavCent(), referenceNav.asOfAt(), referenceNav.validUntil(), referenceNav.source(),
        referenceNav.createdAt())) != 1) {
      throw new IllegalStateException("strategy reference NAV was not appended");
    }
  }

  @Override
  public Optional<StrategyReferenceNav> findApplicable(String ownerUserId, StrategyKey strategyKey, Instant asOfAt) {
    return Optional.ofNullable(mapper.findApplicableReferenceNav(ownerUserId, strategyKey.name(), asOfAt)).map(row ->
        new StrategyReferenceNav(row.strategyReferenceNavId(), row.ownerUserId(), StrategyKey.from(row.strategyKey()),
            CurrencyCode.of(row.currency()), row.referenceNavCent(), row.asOfAt(), row.validUntil(), row.source(),
            row.createdAt()));
  }

  @Override
  public Optional<StrategyEvaluation> findByInput(String ownerUserId, String strategyRuleVersionId,
      byte[] inputHash) {
    return Optional.ofNullable(mapper.findEvaluationByInput(ownerUserId, strategyRuleVersionId, inputHash))
        .map(this::evaluation);
  }

  @Override
  public void appendEvaluation(StrategyEvaluation evaluation, byte[] inputHash) {
    if (mapper.insertEvaluation(new StrategyWorkspaceMapper.EvaluationInsertRow(evaluation.strategyEvaluationId(),
        evaluation.ownerUserId(), evaluation.strategyRuleVersionId(), evaluation.inputVersion(), inputHash,
        evaluation.asOfAt(), evaluation.status().name(), evaluation.resultJson())) != 1) {
      throw new IllegalStateException("strategy evaluation was not appended");
    }
  }

  @Override
  public void appendSignal(StrategySignal signal) {
    if (mapper.insertSignal(new StrategyWorkspaceMapper.SignalRow(signal.strategySignalId(),
        signal.strategyEvaluationId(), signal.signalRunId(), signal.instrumentId(), signal.signalScope().name(),
        signal.signalKey(), signal.signalType().name(), signal.severity(), signal.rankNo(), signal.explanation(),
        signal.asOfAt(), signal.createdAt())) != 1) {
      throw new IllegalStateException("strategy signal was not appended");
    }
  }

  private StrategyActiveRule activeRule(StrategyWorkspaceMapper.ActiveRuleRow row) {
    return new StrategyActiveRule(row.strategyActiveRuleId(), row.ownerUserId(), StrategyKey.from(row.strategyKey()),
        row.strategyRuleVersionId(), row.bindingVersion());
  }

  private StrategyEvaluation evaluation(StrategyWorkspaceMapper.EvaluationRow row) {
    return new StrategyEvaluation(row.strategyEvaluationId(), row.ownerUserId(), StrategyKey.from(row.strategyKey()),
        row.strategyRuleVersionId(), row.inputVersion(), row.asOfAt(), StrategyEvaluationStatus.valueOf(row.status()),
        row.resultJson());
  }

  private StrategyRuleVersion ruleVersion(StrategyWorkspaceMapper.RuleVersionRow row) {
    return new StrategyRuleVersion(row.strategyRuleVersionId(), row.ownerUserId(), StrategyKey.from(row.strategyKey()),
        row.ruleVersion(), row.ruleHash(), row.ruleJson(), StrategyRuleStatus.valueOf(row.status()),
        row.createdByUserId(), row.createdAt());
  }

  private static Instant timestamp(StrategyHistoryCursor cursor) {
    return cursor == null ? null : cursor.timestamp();
  }

  private static String itemId(StrategyHistoryCursor cursor) {
    return cursor == null ? null : cursor.itemId();
  }
}
