package com.personal.investment.ledger.domain;

import java.util.Objects;

/** Immutable read-side Market reference required by the Ledger spot-trade boundary. */
public record TradableInstrument(String instrumentId, TradableInstrumentType type, CurrencyCode nativeCurrency) {
  public TradableInstrument {
    if (instrumentId == null || instrumentId.isBlank()) {
      throw new IllegalArgumentException("instrumentId must not be blank");
    }
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(nativeCurrency, "nativeCurrency must not be null");
  }
}
