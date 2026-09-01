package com.personal.investment.platform.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.platform.application.IdempotencyResponseCodec;
import org.springframework.stereotype.Component;

@Component
public class JacksonIdempotencyResponseCodec implements IdempotencyResponseCodec {
  private final ObjectMapper objectMapper;

  public JacksonIdempotencyResponseCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public String encode(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("could not serialize idempotency response", exception);
    }
  }

  @Override
  public <T> T decode(String value, Class<T> type) {
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("could not deserialize idempotency response", exception);
    }
  }
}
