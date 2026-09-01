package com.personal.investment.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class IdempotencyExecutorTest {
  @Test
  void replaysTheFirstSuccessfulResponseWithoutExecutingTheCommandAgain() {
    InMemoryIdempotencyPort port = new InMemoryIdempotencyPort();
    AtomicInteger executions = new AtomicInteger();
    IdempotencyExecutor executor = new IdempotencyExecutor(port, () -> "01K8D43J4YFN7X9R2B6C8M0V3P",
        new StringResponseCodec());

    IdempotencyResponse<String> first = executor.execute("owner", "POST", "/api/v1/ledger/accounts", "key-1",
        "displayName=美元现金&currency=USD", String.class,
        () -> new IdempotencyResponse<>(200, "account-" + executions.incrementAndGet()));
    IdempotencyResponse<String> replay = executor.execute("owner", "POST", "/api/v1/ledger/accounts", "key-1",
        "displayName=美元现金&currency=USD", String.class,
        () -> new IdempotencyResponse<>(201, "account-" + executions.incrementAndGet()));

    assertThat(first.status()).isEqualTo(200);
    assertThat(first.body()).isEqualTo("account-1");
    assertThat(replay.status()).isEqualTo(200);
    assertThat(replay.body()).isEqualTo("account-1");
    assertThat(executions).hasValue(1);
  }

  @Test
  void rejectsReuseOfTheSameKeyForADifferentSemanticRequest() {
    InMemoryIdempotencyPort port = new InMemoryIdempotencyPort();
    IdempotencyExecutor executor = new IdempotencyExecutor(port, () -> "01K8D43J4YFN7X9R2B6C8M0V3P",
        new StringResponseCodec());
    executor.execute("owner", "POST", "/api/v1/ledger/accounts", "key-1",
        "displayName=美元现金&currency=USD", String.class,
        () -> new IdempotencyResponse<>(201, "account-1"));

    assertThatThrownBy(() -> executor.execute("owner", "POST", "/api/v1/ledger/accounts", "key-1",
        "displayName=人民币现金&currency=CNY", String.class,
        () -> new IdempotencyResponse<>(201, "account-2")))
        .isInstanceOf(IdempotencyException.class)
        .hasMessageContaining("IDEMPOTENCY_KEY_REUSED");
  }

  private static final class InMemoryIdempotencyPort implements IdempotencyPort {
    private final Map<String, IdempotencyRecord> records = new HashMap<>();

    @Override
    public void insertProcessing(IdempotencyRecord record) {
      if (records.putIfAbsent(key(record), record) != null) {
        throw new DuplicateKeyException("duplicate idempotency key");
      }
    }

    @Override
    public Optional<IdempotencyRecord> find(String ownerUserId, String method, String path, String key) {
      return Optional.ofNullable(records.get(key(ownerUserId, method, path, key)));
    }

    @Override
    public void markSucceeded(String idempotencyRecordId, int responseStatus, String responseJson) {
      records.replaceAll((ignored, record) -> record.idempotencyRecordId().equals(idempotencyRecordId)
          ? record.succeeded(responseStatus, responseJson) : record);
    }

    private String key(IdempotencyRecord record) {
      return key(record.ownerUserId(), record.method(), record.path(), record.key());
    }

    private String key(String ownerUserId, String method, String path, String key) {
      return ownerUserId + ":" + method + ":" + path + ":" + key;
    }
  }

  private static final class StringResponseCodec implements IdempotencyResponseCodec {
    @Override
    public String encode(Object value) {
      return (String) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T decode(String value, Class<T> type) {
      return (T) value;
    }
  }
}
