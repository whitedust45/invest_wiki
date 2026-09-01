package com.personal.investment.platform.application;

import java.util.Arrays;
import java.util.Objects;

public record IdempotencyRecord(
    String idempotencyRecordId,
    String ownerUserId,
    String method,
    String path,
    String key,
    byte[] requestHash,
    IdempotencyStatus status,
    Integer responseStatus,
    String responseJson) {
  public IdempotencyRecord {
    requireText(idempotencyRecordId, "idempotencyRecordId");
    requireText(ownerUserId, "ownerUserId");
    requireText(method, "method");
    requireText(path, "path");
    requireText(key, "key");
    if (key.length() > 128) {
      throw new IllegalArgumentException("idempotency key exceeds 128 characters");
    }
    Objects.requireNonNull(requestHash, "requestHash must not be null");
    if (requestHash.length != 32) {
      throw new IllegalArgumentException("requestHash must be SHA-256");
    }
    requestHash = Arrays.copyOf(requestHash, requestHash.length);
    Objects.requireNonNull(status, "status must not be null");
    if (status == IdempotencyStatus.SUCCEEDED && (responseJson == null || responseStatus == null)) {
      throw new IllegalArgumentException("successful idempotency record requires status and responseJson");
    }
    if (responseStatus != null && (responseStatus < 200 || responseStatus >= 300)) {
      throw new IllegalArgumentException("idempotency response status must be 2xx");
    }
  }

  @Override
  public byte[] requestHash() {
    return Arrays.copyOf(requestHash, requestHash.length);
  }

  public IdempotencyRecord succeeded(int responseStatus, String responseJson) {
    return new IdempotencyRecord(idempotencyRecordId, ownerUserId, method, path, key, requestHash,
        IdempotencyStatus.SUCCEEDED, responseStatus, responseJson);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
