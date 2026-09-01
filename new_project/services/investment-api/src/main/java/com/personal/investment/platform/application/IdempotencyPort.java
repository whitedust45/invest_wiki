package com.personal.investment.platform.application;

import java.util.Optional;

public interface IdempotencyPort {
  void insertProcessing(IdempotencyRecord record);

  Optional<IdempotencyRecord> find(String ownerUserId, String method, String path, String key);

  void markSucceeded(String idempotencyRecordId, int responseStatus, String responseJson);
}
