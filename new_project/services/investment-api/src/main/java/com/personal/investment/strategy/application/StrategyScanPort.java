package com.personal.investment.strategy.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StrategyScanPort {
  void append(StrategyScan scan);

  Optional<StrategyScan> find(String ownerUserId, String strategyScanId);

  Optional<StrategyScan> findNextRunnable(Instant reclaimBefore);

  boolean claim(String strategyScanId, Instant reclaimBefore, Instant startedAt);

  void appendItem(StrategyScanItem item);

  List<StrategyScanItem> findItems(String strategyScanId);

  void complete(String strategyScanId, StrategyScanStatus status, String resultJson, Instant completedAt);
}
