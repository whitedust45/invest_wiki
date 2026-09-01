package com.personal.investment.strategy.application;

import com.personal.investment.strategy.domain.StrategyKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StrategyHistoryService {
  private static final int MAX_LIMIT = 100;
  private final StrategyHistoryPort historyPort;

  public StrategyHistoryService(StrategyHistoryPort historyPort) {
    this.historyPort = historyPort;
  }

  @Transactional(readOnly = true)
  public StrategyHistoryPage<StrategyRuleVersion> ruleVersions(String ownerUserId, StrategyKey strategyKey,
      String cursor, int limit) {
    return page(ownerUserId, strategyKey, cursor, limit, historyPort::findRuleVersions,
        value -> new StrategyHistoryCursor(value.createdAt(), value.strategyRuleVersionId()));
  }

  @Transactional(readOnly = true)
  public StrategyHistoryPage<StrategyReferenceNav> referenceNavs(String ownerUserId, StrategyKey strategyKey,
      String cursor, int limit) {
    return page(ownerUserId, strategyKey, cursor, limit, historyPort::findReferenceNavs,
        value -> new StrategyHistoryCursor(value.createdAt(), value.strategyReferenceNavId()));
  }

  @Transactional(readOnly = true)
  public StrategyHistoryPage<StrategyEvaluation> evaluations(String ownerUserId, StrategyKey strategyKey,
      String cursor, int limit) {
    return page(ownerUserId, strategyKey, cursor, limit, historyPort::findEvaluations,
        value -> new StrategyHistoryCursor(value.asOfAt(), value.strategyEvaluationId()));
  }

  private <T> StrategyHistoryPage<T> page(String ownerUserId, StrategyKey strategyKey, String cursorValue, int limit,
      Finder<T> finder, Function<T, StrategyHistoryCursor> cursorOf) {
    if (ownerUserId == null || ownerUserId.isBlank() || strategyKey == null || limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("strategy history request is invalid");
    }
    StrategyHistoryCursor before = cursorValue == null || cursorValue.isBlank() ? null : decode(cursorValue);
    List<T> rows = finder.find(ownerUserId, strategyKey, before, limit + 1);
    boolean hasMore = rows.size() > limit;
    List<T> items = hasMore ? rows.subList(0, limit) : rows;
    return new StrategyHistoryPage<>(items, hasMore ? encode(cursorOf.apply(items.getLast())) : null);
  }

  private static String encode(StrategyHistoryCursor cursor) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(
        (cursor.timestamp().toEpochMilli() + "|" + cursor.itemId()).getBytes(StandardCharsets.UTF_8));
  }

  private static StrategyHistoryCursor decode(String value) {
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
      String[] parts = decoded.split("\\|", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException("strategy history cursor is malformed");
      }
      return new StrategyHistoryCursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), parts[1]);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("strategy history cursor is malformed", exception);
    }
  }

  @FunctionalInterface
  private interface Finder<T> {
    List<T> find(String ownerUserId, StrategyKey strategyKey, StrategyHistoryCursor before, int limit);
  }
}
