package com.personal.investment.platform.application;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyExecutor {
  private final IdempotencyPort port;
  private final LedgerIdGenerator idGenerator;
  private final IdempotencyResponseCodec responseCodec;

  public IdempotencyExecutor(IdempotencyPort port, LedgerIdGenerator idGenerator,
      IdempotencyResponseCodec responseCodec) {
    this.port = port;
    this.idGenerator = idGenerator;
    this.responseCodec = responseCodec;
  }

  @Transactional
  public <T> IdempotencyResponse<T> execute(String ownerUserId, String method, String path, String key,
      String canonicalRequest, Class<T> responseType, Supplier<IdempotencyResponse<T>> operation) {
    validate(ownerUserId, method, path, key, canonicalRequest, responseType, operation);
    byte[] requestHash = sha256(canonicalRequest);
    IdempotencyRecord record = new IdempotencyRecord(idGenerator.next(), ownerUserId, method, path, key,
        requestHash, IdempotencyStatus.PROCESSING, null, null);
    try {
      port.insertProcessing(record);
    } catch (DuplicateKeyException duplicate) {
      return replay(ownerUserId, method, path, key, requestHash, responseType);
    }

    IdempotencyResponse<T> response = Objects.requireNonNull(operation.get(), "operation response must not be null");
    port.markSucceeded(record.idempotencyRecordId(), response.status(), responseCodec.encode(response.body()));
    return response;
  }

  private <T> IdempotencyResponse<T> replay(String ownerUserId, String method, String path, String key, byte[] requestHash,
      Class<T> responseType) {
    IdempotencyRecord existing = port.find(ownerUserId, method, path, key)
        .orElseThrow(() -> new IdempotencyException("IDEMPOTENCY_IN_PROGRESS", "请求仍在处理中"));
    if (!MessageDigest.isEqual(existing.requestHash(), requestHash)) {
      throw new IdempotencyException("IDEMPOTENCY_KEY_REUSED", "幂等键已用于不同请求");
    }
    if (existing.status() != IdempotencyStatus.SUCCEEDED || existing.responseJson() == null
        || existing.responseStatus() == null) {
      throw new IdempotencyException("IDEMPOTENCY_IN_PROGRESS", "请求仍在处理中");
    }
    return new IdempotencyResponse<>(existing.responseStatus(),
        responseCodec.decode(existing.responseJson(), responseType));
  }

  private static byte[] sha256(String canonicalRequest) {
    try {
      return MessageDigest.getInstance("SHA-256")
          .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static void validate(String ownerUserId, String method, String path, String key,
      String canonicalRequest, Class<?> responseType, Supplier<?> operation) {
    requireText(ownerUserId, "ownerUserId");
    requireText(method, "method");
    requireText(path, "path");
    requireText(key, "idempotency key");
    if (key.length() > 128) {
      throw new IllegalArgumentException("idempotency key exceeds 128 characters");
    }
    Objects.requireNonNull(canonicalRequest, "canonicalRequest must not be null");
    Objects.requireNonNull(responseType, "responseType must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
