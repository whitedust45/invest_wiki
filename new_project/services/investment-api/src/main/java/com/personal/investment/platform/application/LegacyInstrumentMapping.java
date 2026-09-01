package com.personal.investment.platform.application;

public record LegacyInstrumentMapping(String module, String symbol, String instrumentId) {
  public LegacyInstrumentMapping {
    module = normalizedModule(module);
    symbol = normalizedSymbol(symbol);
    if (instrumentId == null || instrumentId.isBlank()) {
      throw new IllegalArgumentException("legacy instrument mapping instrumentId must not be blank");
    }
  }

  private static String normalizedModule(String value) {
    return normalized(value, "module").toLowerCase(java.util.Locale.ROOT);
  }

  private static String normalizedSymbol(String value) {
    return normalized(value, "symbol").toUpperCase(java.util.Locale.ROOT);
  }

  private static String normalized(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("legacy instrument mapping " + field + " must not be blank");
    }
    return value.trim();
  }
}
