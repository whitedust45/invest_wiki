package com.personal.investment.strategy.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class StrategyRuleValidatorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void acceptsTheConfirmedQqqRuleUsingOnlyStringWireValues() throws Exception {
    JsonNode rule = objectMapper.readTree("""
        {"starter_percent":"5","target_percent":"10","upper_percent":"12",
         "qld_max_share_percent":"35","moving_average_days":"120"}
        """);

    assertThatCode(() -> StrategyRuleValidator.validate(StrategyKey.QQQ_GROWTH, rule))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsMoneyAsJsonNumberOrWithoutExplicitCurrency() throws Exception {
    JsonNode numberMoney = objectMapper.readTree("""
        {"annual_expense_cent":12000000,"annual_expense_currency":"CNY",
         "minimum_dividend_coverage_percent":"100","cash_buffer_months":"6"}
        """);
    JsonNode missingCurrency = objectMapper.readTree("""
        {"annual_expense_cent":"12000000","minimum_dividend_coverage_percent":"100",
         "cash_buffer_months":"6"}
        """);

    assertThatIllegalArgumentException().isThrownBy(
        () -> StrategyRuleValidator.validate(StrategyKey.HIGH_DIVIDEND, numberMoney))
        .withMessageContaining("string");
    assertThatIllegalArgumentException().isThrownBy(
        () -> StrategyRuleValidator.validate(StrategyKey.HIGH_DIVIDEND, missingCurrency))
        .withMessageContaining("currency");
  }

  @Test
  void rejectsARuleFromAnotherStrategyCurrency() throws Exception {
    JsonNode rule = objectMapper.readTree("""
        {"minimum_pool_cent":"100000000","minimum_pool_currency":"USD",
         "pb_entry_percentile":"30","stress_drop_percent":"20",
         "margin_warning_percent":"60","roll_window_days":"10"}
        """);

    assertThatIllegalArgumentException().isThrownBy(() -> StrategyRuleValidator.validate(StrategyKey.IC_IM, rule))
        .withMessageContaining("CNY");
  }
}
