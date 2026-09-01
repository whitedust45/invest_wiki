package com.personal.investment.platform.application;

public interface IdempotencyResponseCodec {
  String encode(Object value);

  <T> T decode(String value, Class<T> type);
}
