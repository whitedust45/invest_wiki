package com.personal.investment.strategy.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StrategyScanService {
  private static final Duration RECLAIM_AFTER = Duration.ofMinutes(10);
  private final StrategyScanPort scanPort;
  private final StrategyEvaluationService evaluationService;
  private final StrategyIdGenerator idGenerator;
  private final ObjectMapper json;

  public StrategyScanService(StrategyScanPort scanPort, StrategyEvaluationService evaluationService,
      StrategyIdGenerator idGenerator, ObjectMapper json) {
    this.scanPort = scanPort;
    this.evaluationService = evaluationService;
    this.idGenerator = idGenerator;
    this.json = json;
  }

  @Transactional
  public StrategyScan request(String ownerUserId, List<StrategyKey> requestedStrategyKeys) {
    requireOwner(ownerUserId);
    List<StrategyKey> keys = normalizedKeys(requestedStrategyKeys);
    Instant now = Instant.now();
    StrategyScan scan = new StrategyScan(idGenerator.next(), ownerUserId, keys, now, StrategyScanStatus.QUEUED,
        (short) 0, null, null, null, now);
    scanPort.append(scan);
    return scan;
  }

  @Transactional(readOnly = true)
  public StrategyScan find(String ownerUserId, String strategyScanId) {
    requireOwner(ownerUserId);
    if (strategyScanId == null || !strategyScanId.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
      throw new IllegalArgumentException("strategyScanId must be a ULID");
    }
    return scanPort.find(ownerUserId, strategyScanId)
        .orElseThrow(() -> new IllegalArgumentException("strategy scan was not found"));
  }

  @Transactional(readOnly = true)
  public List<StrategyScanItem> items(String ownerUserId, String strategyScanId) {
    find(ownerUserId, strategyScanId);
    return scanPort.findItems(strategyScanId);
  }

  /** Called by the local worker; each evaluation still only reads persisted facts and snapshots. */
  @Transactional
  public boolean runOneQueuedScan() {
    Instant now = Instant.now();
    Optional<StrategyScan> candidate = scanPort.findNextRunnable(now.minus(RECLAIM_AFTER));
    if (candidate.isEmpty() || !scanPort.claim(candidate.get().strategyScanId(), now.minus(RECLAIM_AFTER), now)) {
      return false;
    }
    execute(candidate.get(), now);
    return true;
  }

  @Transactional
  public void execute(StrategyScan scan, Instant startedAt) {
    List<StrategyScanItem> existing = scanPort.findItems(scan.strategyScanId());
    EnumSet<StrategyKey> completed = EnumSet.noneOf(StrategyKey.class);
    existing.forEach(item -> completed.add(item.strategyKey()));
    List<StrategyScanItem> outcomes = new ArrayList<>(existing);
    for (StrategyKey key : scan.strategyKeys()) {
      if (completed.contains(key)) {
        continue;
      }
      try {
        StrategyEvaluation evaluation = evaluationService.evaluate(scan.ownerUserId(), key, scan.asOfAt());
        StrategyScanItem item = new StrategyScanItem(idGenerator.next(), scan.strategyScanId(), key,
            evaluation.strategyEvaluationId(), evaluation.status().name(), null, null, Instant.now());
        scanPort.appendItem(item);
        outcomes.add(item);
      } catch (RuntimeException exception) {
        StrategyScanItem item = new StrategyScanItem(idGenerator.next(), scan.strategyScanId(), key, null, "FAILED",
            exception.getClass().getSimpleName(), safeMessage(exception), Instant.now());
        scanPort.appendItem(item);
        outcomes.add(item);
      }
    }
    StrategyScanStatus status = terminalStatus(outcomes);
    scanPort.complete(scan.strategyScanId(), status, result(outcomes), Instant.now());
  }

  private StrategyScanStatus terminalStatus(List<StrategyScanItem> outcomes) {
    long failures = outcomes.stream().filter(item -> "FAILED".equals(item.status())).count();
    if (failures == outcomes.size()) {
      return StrategyScanStatus.FAILED;
    }
    if (failures > 0) {
      return StrategyScanStatus.PARTIAL_SUCCEEDED;
    }
    return outcomes.stream().allMatch(item -> StrategyEvaluationStatus.DATA_STALE.name().equals(item.status()))
        ? StrategyScanStatus.SKIPPED_STALE : StrategyScanStatus.SUCCEEDED;
  }

  private String result(List<StrategyScanItem> outcomes) {
    try {
      return json.writeValueAsString(new ScanResult(outcomes.size(),
          outcomes.stream().filter(item -> "FAILED".equals(item.status())).count(),
          outcomes.stream().filter(item -> StrategyEvaluationStatus.DATA_STALE.name().equals(item.status())).count()));
    } catch (Exception exception) {
      throw new IllegalStateException("strategy scan result could not be encoded", exception);
    }
  }

  private static List<StrategyKey> normalizedKeys(List<StrategyKey> requested) {
    if (requested == null || requested.isEmpty()) {
      return List.of(StrategyKey.values());
    }
    EnumSet<StrategyKey> keys = EnumSet.noneOf(StrategyKey.class);
    for (StrategyKey key : requested) {
      if (key == null || !keys.add(key)) {
        throw new StrategyValidationException(StrategyValidationCode.STRATEGY_INPUT_INVALID,
            "strategyKeys must contain distinct strategy keys");
      }
    }
    return Arrays.stream(StrategyKey.values()).filter(keys::contains).toList();
  }

  private static void requireOwner(String ownerUserId) {
    if (ownerUserId == null || !ownerUserId.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
      throw new IllegalArgumentException("ownerUserId must be a ULID");
    }
  }

  private static String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    return message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(message.length(), 512));
  }

  private record ScanResult(int attempted, long failed, long stale) {
  }
}
