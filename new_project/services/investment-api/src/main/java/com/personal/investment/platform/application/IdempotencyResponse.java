package com.personal.investment.platform.application;

import java.util.Objects;

/** Immutable successful HTTP response persisted for idempotent replay. */
public record IdempotencyResponse<T>(int status, T body) {
  public IdempotencyResponse {
    if (status < 200 || status >= 300) {
      throw new IllegalArgumentException("idempotency response status must be 2xx");
    }
    Objects.requireNonNull(body, "idempotency response body must not be null");
  }
}
