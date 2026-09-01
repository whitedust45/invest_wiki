package com.personal.investment.strategy.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.strategy.application.StrategyScan;
import com.personal.investment.strategy.application.StrategyScanItem;
import com.personal.investment.strategy.application.StrategyScanPort;
import com.personal.investment.strategy.application.StrategyScanStatus;
import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MyBatisStrategyScanAdapter implements StrategyScanPort {
  private final StrategyScanMapper mapper;
  private final ObjectMapper json;

  public MyBatisStrategyScanAdapter(StrategyScanMapper mapper, ObjectMapper json) {
    this.mapper = mapper;
    this.json = json;
  }

  @Override
  public void append(StrategyScan scan) {
    if (mapper.insert(new StrategyScanMapper.ScanRow(scan.strategyScanId(), scan.ownerUserId(), keys(scan.strategyKeys()),
        scan.asOfAt(), scan.status().name(), scan.attemptNo(), scan.startedAt(), scan.completedAt(), scan.resultJson(),
        scan.createdAt())) != 1) {
      throw new IllegalStateException("strategy scan was not appended");
    }
  }

  @Override
  public Optional<StrategyScan> find(String ownerUserId, String strategyScanId) {
    return Optional.ofNullable(mapper.find(ownerUserId, strategyScanId)).map(this::scan);
  }

  @Override
  public Optional<StrategyScan> findNextRunnable(Instant reclaimBefore) {
    return Optional.ofNullable(mapper.findNextRunnable(reclaimBefore)).map(this::scan);
  }

  @Override
  public boolean claim(String strategyScanId, Instant reclaimBefore, Instant startedAt) {
    return mapper.claim(strategyScanId, reclaimBefore, startedAt) == 1;
  }

  @Override
  public void appendItem(StrategyScanItem item) {
    if (mapper.insertItem(new StrategyScanMapper.ScanItemRow(item.strategyScanItemId(), item.strategyScanId(),
        item.strategyKey().name(), item.strategyEvaluationId(), item.status(), item.failureCode(),
        item.failureMessage(), item.createdAt())) != 1) {
      throw new IllegalStateException("strategy scan item was not appended");
    }
  }

  @Override
  public List<StrategyScanItem> findItems(String strategyScanId) {
    return mapper.findItems(strategyScanId).stream().map(row -> new StrategyScanItem(row.strategyScanItemId(),
        row.strategyScanId(), StrategyKey.from(row.strategyKey()), row.strategyEvaluationId(), row.status(),
        row.failureCode(), row.failureMessage(), row.createdAt())).toList();
  }

  @Override
  public void complete(String strategyScanId, StrategyScanStatus status, String resultJson, Instant completedAt) {
    if (mapper.complete(strategyScanId, status.name(), resultJson, completedAt) != 1) {
      throw new IllegalStateException("strategy scan was not running when completion was attempted");
    }
  }

  private StrategyScan scan(StrategyScanMapper.ScanRow row) {
    return new StrategyScan(row.strategyScanId(), row.ownerUserId(), readKeys(row.requestedStrategyKeysJson()), row.asOfAt(),
        StrategyScanStatus.valueOf(row.status()), row.attemptNo(), row.startedAt(), row.completedAt(), row.resultJson(),
        row.createdAt());
  }

  private String keys(List<StrategyKey> keys) {
    try {
      return json.writeValueAsString(keys.stream().map(StrategyKey::name).toList());
    } catch (Exception exception) {
      throw new IllegalStateException("strategy scan keys could not be encoded", exception);
    }
  }

  private List<StrategyKey> readKeys(String value) {
    try {
      return json.readValue(value, new TypeReference<List<String>>() { }).stream().map(StrategyKey::from).toList();
    } catch (Exception exception) {
      throw new IllegalStateException("persisted strategy scan keys are invalid", exception);
    }
  }
}
