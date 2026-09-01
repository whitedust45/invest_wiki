package com.personal.investment.portfolio.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Owner-scoped historical ledger state used only to compare against an explicit broker snapshot. */
public record LedgerReconciliationSnapshot(CurrencyCode currency, long cashCent,
                                           Map<String, BigDecimal> positions, long sourceLedgerVersion) {
  public LedgerReconciliationSnapshot {
    Objects.requireNonNull(currency, "currency must not be null");
    if (cashCent < 0 || sourceLedgerVersion < 0) {
      throw new IllegalArgumentException("ledger reconciliation snapshot values must not be negative");
    }
    Map<String, BigDecimal> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, BigDecimal> entry : Objects.requireNonNull(positions, "positions must not be null").entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().signum() <= 0
          || entry.getValue().scale() > 8) {
        throw new IllegalArgumentException("ledger reconciliation snapshot position is invalid");
      }
      normalized.put(entry.getKey(), entry.getValue().setScale(8, RoundingMode.UNNECESSARY));
    }
    positions = Map.copyOf(normalized);
  }
}
