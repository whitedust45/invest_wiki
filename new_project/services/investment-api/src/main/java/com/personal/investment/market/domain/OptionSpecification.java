package com.personal.investment.market.domain;

/** Immutable long-option metadata. Contract multiplier is a non-monetary integer. */
public record OptionSpecification(String underlyingInstrumentId, OptionRight optionRight, long strikePriceCent,
                                  long contractMultiplier) {
  public OptionSpecification {
    if (underlyingInstrumentId == null || underlyingInstrumentId.isBlank()) {
      throw new IllegalArgumentException("option underlyingInstrumentId must not be blank");
    }
    if (optionRight == null) {
      throw new IllegalArgumentException("optionRight must not be null");
    }
    if (strikePriceCent <= 0) {
      throw new IllegalArgumentException("option strikePriceCent must be positive");
    }
    if (contractMultiplier <= 0) {
      throw new IllegalArgumentException("option contractMultiplier must be positive");
    }
  }
}
