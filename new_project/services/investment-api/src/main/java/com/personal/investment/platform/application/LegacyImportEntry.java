package com.personal.investment.platform.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;

/** One legacy `entries` array item. It preserves the raw object so conversion never relies on lossy doubles. */
public record LegacyImportEntry(int sourceRow, String module, String action, JsonNode raw) {
  public LegacyImportEntry {
    if (sourceRow < 1) {
      throw new IllegalArgumentException("sourceRow must start at 1");
    }
    module = normalized(module, "module");
    action = normalized(action, "action");
    if (raw == null || !raw.isObject()) {
      throw new IllegalArgumentException("legacy entry must be a JSON object");
    }
  }

  public JsonNode field(String name) {
    return raw.path(name);
  }

  private static String normalized(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("legacy entry " + field + " must not be blank");
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
