package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable Market-data snapshot needed by a futures command. */
public record FuturesInstrument(String instrumentId, CurrencyCode currency, LocalDate maturityDate,
                                long contractMultiplierCent) {
  public FuturesInstrument {
    if (instrumentId == null || instrumentId.isBlank() || contractMultiplierCent <= 0) {
      throw new IllegalArgumentException("futures instrument is invalid");
    }
    Objects.requireNonNull(currency, "currency must not be null");
    Objects.requireNonNull(maturityDate, "maturityDate must not be null");
  }
}
