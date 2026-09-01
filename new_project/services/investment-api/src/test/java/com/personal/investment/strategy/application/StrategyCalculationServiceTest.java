package com.personal.investment.strategy.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.strategy.domain.StrategyKey;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StrategyCalculationServiceTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final StrategyCalculationService service = new StrategyCalculationService(JSON);

  @Test
  void evaluatesHighDividendFromNativeCurrencyIncomeAndCashBuffer() throws Exception {
    StrategyCalculationResult result = service.evaluate(StrategyKey.HIGH_DIVIDEND,
        JSON.readTree("""
            {"annual_expense_cent":"12000000","annual_expense_currency":"CNY",
             "minimum_dividend_coverage_percent":"100","cash_buffer_months":"6"}
            """),
        new HighDividendCalculationInput("ledger:42|market:N/A", true, false, 8_000_000L, 12_600_000L));

    assertThat(result.status()).isEqualTo(StrategyEvaluationStatus.IN_RANGE);
    assertThat(result.resultJson()).contains("income_coverage_percent", "cash_buffer_cent", "IN_RANGE");
  }

  @Test
  void evaluatesQqqAgainstExplicitUsdReferenceNavAndQldLimit() throws Exception {
    StrategyCalculationResult result = service.evaluate(StrategyKey.QQQ_GROWTH,
        JSON.readTree("""
            {"starter_percent":"5","target_percent":"10","upper_percent":"12",
             "qld_max_share_percent":"35","moving_average_days":"120"}
            """),
        new QqqGrowthCalculationInput("ledger:42|market:quote-r8|reference:nav-r3", true, false, true,
            100_000L, 9_000L, 2_000L));

    assertThat(result.status()).isEqualTo(StrategyEvaluationStatus.IN_RANGE);
    assertThat(result.resultJson()).contains("allocation_percent", "qld_share_percent", "IN_RANGE");
  }

  @Test
  void evaluatesIcImWithPoolPbMarginAndRollWindow() throws Exception {
    StrategyCalculationResult result = service.evaluate(StrategyKey.IC_IM,
        JSON.readTree("""
            {"minimum_pool_cent":"100000000","minimum_pool_currency":"CNY",
             "pb_entry_percentile":"30","stress_drop_percent":"20","margin_warning_percent":"60",
             "roll_window_days":"10"}
            """),
        new IcImCalculationInput("ledger:42|market:basis-r5", true, false, true, true,
            120_000_000L, 30_000_000L, 20_000_000L, "28.40", "44.20", "-0.0825", "-0.0741", 18));

    assertThat(result.status()).isEqualTo(StrategyEvaluationStatus.IN_RANGE);
    assertThat(result.resultJson()).contains("pool_cent", "margin_risk_percent", "ic_pb_percentile");
  }

  @Test
  void evaluatesDeepPutBudgetAndExpiryLadder() throws Exception {
    StrategyCalculationResult result = service.evaluate(StrategyKey.DEEP_PUT,
        JSON.readTree("""
            {"budget_min_percent":"0.5","budget_max_percent":"2","expiry_warning_days":"30"}
        """),
        new DeepPutCalculationInput("ledger:42|market:N/A|reference:nav-r3", true, false, true, true,
            100_000L, 1_200L, BigDecimal.valueOf(2), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 9, 14)));

    assertThat(result.status()).isEqualTo(StrategyEvaluationStatus.IN_RANGE);
    assertThat(result.resultJson()).contains("premium_budget_percent", "open_put_quantity", "IN_RANGE");
  }

  @Test
  void reportsCrossCurrencyBeforeCalculatingAnyRatio() throws Exception {
    StrategyCalculationResult result = service.evaluate(StrategyKey.HIGH_DIVIDEND,
        JSON.readTree("""
            {"annual_expense_cent":"12000000","annual_expense_currency":"CNY",
             "minimum_dividend_coverage_percent":"100","cash_buffer_months":"6"}
            """),
        new HighDividendCalculationInput("ledger:42|market:N/A", true, true, 8_000_000L, 12_600_000L));

    assertThat(result.status()).isEqualTo(StrategyEvaluationStatus.CROSS_CURRENCY_UNVALUED);
    assertThat(result.resultJson()).contains("currency_mismatch");
  }

  @Test
  void reportsStaleWhenARequiredMarketOrReferenceInputIsUnavailable() throws Exception {
    StrategyCalculationResult result = service.evaluate(StrategyKey.QQQ_GROWTH,
        JSON.readTree("""
            {"starter_percent":"5","target_percent":"10","upper_percent":"12",
             "qld_max_share_percent":"35","moving_average_days":"120"}
            """),
        new QqqGrowthCalculationInput("ledger:42|market:NONE|reference:NONE", true, false, false,
            0L, 0L, 0L));

    assertThat(result.status()).isEqualTo(StrategyEvaluationStatus.DATA_STALE);
    assertThat(result.resultJson()).contains("reference_nav", "market_snapshot");
  }
}
