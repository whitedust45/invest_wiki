package com.personal.investment.platform.application;

public class IdempotencyException extends RuntimeException {
  private final String code;

  public IdempotencyException(String code, String message) {
    super(code + ": " + message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
