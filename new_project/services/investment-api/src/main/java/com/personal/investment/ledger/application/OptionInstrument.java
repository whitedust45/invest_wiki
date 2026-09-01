package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable read-side option contract snapshot required before a long-option command may proceed. */
public record OptionInstrument(String instrumentId, CurrencyCode currency, LocalDate maturityDate,
                               long contractMultiplier) {
  public OptionInstrument {
    if (instrumentId == null || instrumentId.isBlank() || contractMultiplier <= 0) {
      throw new IllegalArgumentException("option instrument is invalid");
    }
    Objects.requireNonNull(currency, "option currency must not be null");
    Objects.requireNonNull(maturityDate, "option maturityDate must not be null");
  }
}
