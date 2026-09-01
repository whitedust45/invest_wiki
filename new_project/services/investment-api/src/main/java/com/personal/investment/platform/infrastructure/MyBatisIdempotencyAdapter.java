package com.personal.investment.platform.infrastructure;

import com.personal.investment.platform.application.IdempotencyPort;
import com.personal.investment.platform.application.IdempotencyRecord;
import com.personal.investment.platform.application.IdempotencyStatus;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MyBatisIdempotencyAdapter implements IdempotencyPort {
  private final IdempotencyMapper mapper;

  public MyBatisIdempotencyAdapter(IdempotencyMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void insertProcessing(IdempotencyRecord record) {
    mapper.insertProcessing(toRow(record));
  }

  @Override
  public Optional<IdempotencyRecord> find(String ownerUserId, String method, String path, String key) {
    return Optional.ofNullable(mapper.find(ownerUserId, method, path, key)).map(this::toDomain);
  }

  @Override
  public void markSucceeded(String idempotencyRecordId, int responseStatus, String responseJson) {
    if (mapper.markSucceeded(idempotencyRecordId, responseStatus, responseJson) != 1) {
      throw new IllegalStateException("idempotency record is no longer processing");
    }
  }

  private IdempotencyMapper.Row toRow(IdempotencyRecord record) {
    return new IdempotencyMapper.Row(record.idempotencyRecordId(), record.ownerUserId(), record.method(),
        record.path(), record.key(), record.requestHash(), record.status().name(), record.responseStatus(),
        record.responseJson());
  }

  private IdempotencyRecord toDomain(IdempotencyMapper.Row row) {
    return new IdempotencyRecord(row.idempotencyRecordId(), row.ownerUserId(), row.method(), row.path(),
        row.key(), row.requestHash(), IdempotencyStatus.valueOf(row.status()), row.responseStatus(),
        row.responseJson());
  }
}
