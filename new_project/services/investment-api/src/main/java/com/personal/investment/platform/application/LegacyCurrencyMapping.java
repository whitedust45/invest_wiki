package com.personal.investment.platform.application;

import com.personal.investment.ledger.domain.CurrencyCode;

public record LegacyCurrencyMapping(String module, String action, CurrencyCode currency,
                                    LegacyAmountUnit amountUnit, String cashAccountId) {
  public LegacyCurrencyMapping {
    module = normalized(module, "module");
    action = action == null || action.isBlank() ? null : normalized(action, "action");
    if (currency == null || amountUnit == null || cashAccountId == null || cashAccountId.isBlank()) {
      throw new IllegalArgumentException("legacy currency mapping is incomplete");
    }
  }

  private static String normalized(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("legacy currency mapping " + field + " must not be blank");
    }
    return value.trim().toLowerCase(java.util.Locale.ROOT);
  }
}
