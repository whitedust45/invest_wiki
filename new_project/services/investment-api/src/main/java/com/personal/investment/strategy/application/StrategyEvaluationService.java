package com.personal.investment.strategy.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.strategy.domain.StrategyKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StrategyEvaluationService {
  private final StrategyWorkspacePort workspacePort;
  private final StrategyReferenceNavPort referenceNavPort;
  private final StrategyCalculationInputPort calculationInputPort;
  private final StrategyCalculationService calculationService;
  private final StrategyEvaluationPort evaluationPort;
  private final StrategyIdGenerator idGenerator;
  private final ObjectMapper json;

  public StrategyEvaluationService(StrategyWorkspacePort workspacePort, StrategyReferenceNavPort referenceNavPort,
      StrategyCalculationInputPort calculationInputPort, StrategyCalculationService calculationService,
      StrategyEvaluationPort evaluationPort, StrategyIdGenerator idGenerator, ObjectMapper json) {
    this.workspacePort = workspacePort;
    this.referenceNavPort = referenceNavPort;
    this.calculationInputPort = calculationInputPort;
    this.calculationService = calculationService;
    this.evaluationPort = evaluationPort;
    this.idGenerator = idGenerator;
    this.json = json;
  }

  @Transactional
  public StrategyEvaluation evaluate(String ownerUserId, StrategyKey strategyKey, Instant asOfAt) {
    if (ownerUserId == null || !ownerUserId.matches("[0-9A-HJKMNP-TV-Z]{26}") || strategyKey == null
        || asOfAt == null) {
      throw new IllegalArgumentException("strategy evaluation request is invalid");
    }
    if (asOfAt.isAfter(Instant.now())) {
      throw new StrategyValidationException(StrategyValidationCode.STRATEGY_INPUT_INVALID,
          "evaluation date must not be in the future");
    }
    Optional<StrategyActiveRule> active = workspacePort.findActiveRule(ownerUserId, strategyKey);
    if (active.isEmpty()) {
      return new StrategyEvaluation(null, ownerUserId, strategyKey, null, "", asOfAt,
          StrategyEvaluationStatus.BLOCKED,
          "{\"currency\":\"" + strategyKey.currency().name()
              + "\",\"missingOrStaleFields\":[\"active_rule\"],\"signals\":[{\"signalType\":\"BLOCKED\",\"explanation\":\"等待策略规则。\"}]}");
    }
    StrategyRuleVersion rule = workspacePort.findRuleVersion(ownerUserId, active.get().strategyRuleVersionId())
        .orElse(null);
    if (rule == null || rule.strategyKey() != strategyKey || rule.status() != StrategyRuleStatus.ACTIVE) {
      return appendBlocked(ownerUserId, strategyKey, active.get().strategyRuleVersionId(), asOfAt,
          "rule:" + active.get().strategyRuleVersionId() + "|rule:NONE", "active_rule_body");
    }
    StrategyReferenceNav referenceNav = usesUsdReferenceNav(strategyKey)
        ? referenceNavPort.findApplicable(ownerUserId, strategyKey, asOfAt).orElse(null) : null;
    StrategyCalculationInput input = calculationInputPort.load(ownerUserId, strategyKey, asOfAt, referenceNav);
    String inputVersion = "rule:" + active.get().strategyRuleVersionId() + "|" + input.inputVersion();
    byte[] inputHash = sha256(inputVersion);
    Optional<StrategyEvaluation> existing = evaluationPort.findByInput(ownerUserId,
        active.get().strategyRuleVersionId(), inputHash);
    if (existing.isPresent()) {
      return existing.get();
    }
    StrategyCalculationResult result = calculationService.evaluate(strategyKey, parseRule(rule.ruleJson()), input,
        inputVersion);
    StrategyEvaluation evaluation = new StrategyEvaluation(idGenerator.next(), ownerUserId, strategyKey,
        active.get().strategyRuleVersionId(), inputVersion, asOfAt, result.status(), result.resultJson());
    evaluationPort.appendEvaluation(evaluation, inputHash);
    evaluationPort.appendSignal(new StrategySignal(idGenerator.next(), evaluation.strategyEvaluationId(), null, null,
        StrategySignalScope.STRATEGY, "strategy_status", result.status(), severity(result.status()), (short) 0,
        result.explanation(), asOfAt,
        Instant.now()));
    return evaluation;
  }

  private StrategyEvaluation appendBlocked(String ownerUserId, StrategyKey strategyKey, String ruleVersionId,
      Instant asOfAt, String inputVersion, String missing) {
    byte[] inputHash = sha256(inputVersion);
    Optional<StrategyEvaluation> existing = evaluationPort.findByInput(ownerUserId, ruleVersionId, inputHash);
    if (existing.isPresent()) {
      return existing.get();
    }
    String explanation = "策略活动规则缺少不可变规则内容。";
    StrategyEvaluation evaluation = new StrategyEvaluation(idGenerator.next(), ownerUserId, strategyKey,
        ruleVersionId, inputVersion, asOfAt, StrategyEvaluationStatus.BLOCKED,
        resultJson(strategyKey, missing, StrategyEvaluationStatus.BLOCKED, explanation));
    evaluationPort.appendEvaluation(evaluation, inputHash);
    evaluationPort.appendSignal(new StrategySignal(idGenerator.next(), evaluation.strategyEvaluationId(), null, null,
        StrategySignalScope.STRATEGY, "strategy_status", StrategyEvaluationStatus.BLOCKED, "WARNING", (short) 0,
        explanation, asOfAt, Instant.now()));
    return evaluation;
  }

  private static boolean usesUsdReferenceNav(StrategyKey strategyKey) {
    return strategyKey == StrategyKey.QQQ_GROWTH || strategyKey == StrategyKey.DEEP_PUT;
  }

  private static String resultJson(StrategyKey strategyKey, String missing, StrategyEvaluationStatus status,
      String explanation) {
    return "{\"currency\":\"" + strategyKey.currency().name() + "\",\"missingOrStaleFields\":[\"" + missing
        + "\"],\"signals\":[{\"signalType\":\"" + status.name() + "\",\"explanation\":\"" + explanation
        + "\"}]}";
  }

  private JsonNode parseRule(String value) {
    try {
      return json.readTree(value);
    } catch (Exception exception) {
      throw new IllegalStateException("persisted strategy rule is not valid JSON", exception);
    }
  }

  private static String severity(StrategyEvaluationStatus status) {
    return status == StrategyEvaluationStatus.IN_RANGE ? "INFO" : "WARNING";
  }

  private static byte[] sha256(String input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
