package com.personal.investment.strategy.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;

/** Validates rule JSON at the domain boundary so money cannot leak in as a floating point value. */
public final class StrategyRuleValidator {
  private StrategyRuleValidator() {
  }

  public static void validate(StrategyKey strategyKey, JsonNode rule) {
    if (strategyKey == null || rule == null || !rule.isObject()) {
      throw new IllegalArgumentException("strategy rule must be an object");
    }
    validateMinorUnitEncoding(rule);
    switch (strategyKey) {
      case HIGH_DIVIDEND -> highDividend(rule);
      case QQQ_GROWTH -> qqq(rule);
      case IC_IM -> icIm(rule);
      case DEEP_PUT -> deepPut(rule);
    }
  }

  private static void highDividend(JsonNode rule) {
    positiveMinorUnit(rule, "annual_expense_cent", "annual_expense_currency", "CNY");
    percent(rule, "minimum_dividend_coverage_percent", BigDecimal.ZERO, new BigDecimal("10000"));
    positiveWholeNumber(rule, "cash_buffer_months");
  }

  private static void qqq(JsonNode rule) {
    BigDecimal starter = percent(rule, "starter_percent", BigDecimal.ZERO, new BigDecimal("100"));
    BigDecimal target = percent(rule, "target_percent", BigDecimal.ZERO, new BigDecimal("100"));
    BigDecimal upper = percent(rule, "upper_percent", BigDecimal.ZERO, new BigDecimal("100"));
    if (starter.compareTo(target) > 0 || target.compareTo(upper) > 0) {
      throw new IllegalArgumentException("QQQ allocation range must be ordered");
    }
    percent(rule, "qld_max_share_percent", BigDecimal.ZERO, new BigDecimal("100"));
    positiveWholeNumber(rule, "moving_average_days");
  }

  private static void icIm(JsonNode rule) {
    positiveMinorUnit(rule, "minimum_pool_cent", "minimum_pool_currency", "CNY");
    percent(rule, "pb_entry_percentile", BigDecimal.ZERO, new BigDecimal("100"));
    percent(rule, "stress_drop_percent", BigDecimal.ZERO, new BigDecimal("100"));
    percent(rule, "margin_warning_percent", BigDecimal.ZERO, new BigDecimal("100"));
    positiveWholeNumber(rule, "roll_window_days");
  }

  private static void deepPut(JsonNode rule) {
    BigDecimal minimum = percent(rule, "budget_min_percent", BigDecimal.ZERO, new BigDecimal("100"));
    BigDecimal maximum = percent(rule, "budget_max_percent", BigDecimal.ZERO, new BigDecimal("100"));
    if (minimum.compareTo(maximum) > 0) {
      throw new IllegalArgumentException("put budget range must be ordered");
    }
    positiveWholeNumber(rule, "expiry_warning_days");
  }

  private static void validateMinorUnitEncoding(JsonNode value) {
    if (value.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (field.getKey().endsWith("_cent") && !field.getValue().isTextual()) {
          throw new IllegalArgumentException(field.getKey() + " must be a decimal integer string");
        }
        if (field.getKey().endsWith("_cent") && !hasExplicitCurrency(value, field.getKey())) {
          throw new IllegalArgumentException(field.getKey() + " requires an explicit currency field");
        }
        validateMinorUnitEncoding(field.getValue());
      }
    } else if (value.isArray()) {
      for (JsonNode item : value) {
        validateMinorUnitEncoding(item);
      }
    }
  }

  private static boolean hasExplicitCurrency(JsonNode object, String amountField) {
    String amountPrefix = amountField.substring(0, amountField.length() - "_cent".length());
    JsonNode namedCurrency = object.get(amountPrefix + "_currency");
    JsonNode contextualCurrency = object.get("currency");
    return namedCurrency != null && namedCurrency.isTextual() && !namedCurrency.textValue().isBlank()
        || contextualCurrency != null && contextualCurrency.isTextual() && !contextualCurrency.textValue().isBlank();
  }

  private static void positiveMinorUnit(JsonNode rule, String amountField, String currencyField,
      String expectedCurrency) {
    String value = requiredText(rule, amountField);
    if (!value.matches("[1-9][0-9]*")) {
      throw new IllegalArgumentException(amountField + " must be a positive decimal integer string");
    }
    String currency = requiredText(rule, currencyField);
    if (!expectedCurrency.equals(currency)) {
      throw new IllegalArgumentException(amountField + " currency must be " + expectedCurrency);
    }
  }

  private static BigDecimal percent(JsonNode rule, String field, BigDecimal minimum, BigDecimal maximum) {
    String value = requiredText(rule, field);
    if (!value.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,4})?")) {
      throw new IllegalArgumentException(field + " must be a decimal string");
    }
    BigDecimal decimal = new BigDecimal(value);
    if (decimal.compareTo(minimum) < 0 || decimal.compareTo(maximum) > 0) {
      throw new IllegalArgumentException(field + " is out of range");
    }
    return decimal;
  }

  private static void positiveWholeNumber(JsonNode rule, String field) {
    String value = requiredText(rule, field);
    if (!value.matches("[1-9][0-9]*")) {
      throw new IllegalArgumentException(field + " must be a positive whole-number string");
    }
  }

  private static String requiredText(JsonNode rule, String field) {
    JsonNode value = rule.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw new IllegalArgumentException(field + " is required as a string");
    }
    return value.textValue();
  }
}
