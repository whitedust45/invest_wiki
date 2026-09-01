package com.personal.investment.strategy.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.investment.strategy.domain.StrategyKey;
import com.personal.investment.strategy.domain.StrategyRuleValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StrategyRuleVersionService {
  private final StrategyRuleVersionPort ruleVersionPort;
  private final StrategyIdGenerator idGenerator;

  public StrategyRuleVersionService(StrategyRuleVersionPort ruleVersionPort, StrategyIdGenerator idGenerator) {
    this.ruleVersionPort = ruleVersionPort;
    this.idGenerator = idGenerator;
  }

  @Transactional
  public StrategyRuleVersion create(String ownerUserId, StrategyKey strategyKey, String ruleVersion, JsonNode rule,
      String expectedActiveRuleVersionId) {
    requireOwner(ownerUserId);
    if (ruleVersion == null || ruleVersion.isBlank() || ruleVersion.length() > 64) {
      throw new IllegalArgumentException("ruleVersion must contain 1 to 64 characters");
    }
    try {
      StrategyRuleValidator.validate(strategyKey, rule);
    } catch (IllegalArgumentException exception) {
      throw classifyRuleValidation(exception);
    }
    Optional<StrategyActiveRule> current = ruleVersionPort.lockActiveRule(ownerUserId, strategyKey);
    if (current.isEmpty() && expectedActiveRuleVersionId != null) {
      throw new StrategyRuleVersionConflictException();
    }
    if (current.isPresent() && !current.get().strategyRuleVersionId().equals(expectedActiveRuleVersionId)) {
      throw new StrategyRuleVersionConflictException();
    }
    Instant now = Instant.now();
    StrategyRuleVersion created = new StrategyRuleVersion(idGenerator.next(), ownerUserId, strategyKey, ruleVersion,
        sha256(rule.toString()), rule.toString(), StrategyRuleStatus.ACTIVE, ownerUserId, now);
    ruleVersionPort.appendRule(created);
    StrategyActiveRule next = new StrategyActiveRule(current.map(StrategyActiveRule::strategyActiveRuleId)
        .orElseGet(idGenerator::next), ownerUserId, strategyKey,
        created.strategyRuleVersionId(), current.map(value -> value.bindingVersion() + 1).orElse(1L));
    if (current.isEmpty()) {
      ruleVersionPort.createActiveRule(next);
    } else {
      ruleVersionPort.replaceActiveRule(next, expectedActiveRuleVersionId);
      ruleVersionPort.archiveRule(ownerUserId, expectedActiveRuleVersionId);
    }
    ruleVersionPort.appendActiveRuleEvent(new StrategyActiveRuleEvent(idGenerator.next(), ownerUserId, strategyKey,
        current.map(StrategyActiveRule::strategyRuleVersionId).orElse(null), created.strategyRuleVersionId(),
        next.bindingVersion(), ownerUserId, now));
    return created;
  }

  private static void requireOwner(String ownerUserId) {
    if (ownerUserId == null || !ownerUserId.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
      throw new IllegalArgumentException("ownerUserId must be a ULID");
    }
  }

  private static StrategyValidationException classifyRuleValidation(IllegalArgumentException exception) {
    String message = exception.getMessage() == null ? "strategy rule is invalid" : exception.getMessage();
    if (message.contains("currency must be")) {
      return new StrategyValidationException(StrategyValidationCode.STRATEGY_CURRENCY_INVALID, message);
    }
    if (message.contains("_cent")) {
      return new StrategyValidationException(StrategyValidationCode.MONEY_CONVENTION_VIOLATION, message);
    }
    return new StrategyValidationException(StrategyValidationCode.STRATEGY_RULE_INVALID, message);
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
