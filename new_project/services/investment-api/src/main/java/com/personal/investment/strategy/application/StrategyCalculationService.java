package com.personal.investment.strategy.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personal.investment.strategy.domain.StrategyKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Pure calculation boundary for the four strategy workspaces. It has no write, quote-provider or clock dependency,
 * so an evaluation can only be built from immutable rules plus an already-confirmed local input snapshot.
 */
@Service
public class StrategyCalculationService {
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private final ObjectMapper json;

  public StrategyCalculationService(ObjectMapper json) {
    this.json = json;
  }

  public StrategyCalculationResult evaluate(StrategyKey strategyKey, JsonNode rule, StrategyCalculationInput input) {
    return evaluate(strategyKey, rule, input, input == null ? null : input.inputVersion());
  }

  /** The caller prefixes the data version with the immutable rule-version identifier before persistence. */
  public StrategyCalculationResult evaluate(StrategyKey strategyKey, JsonNode rule, StrategyCalculationInput input,
      String inputVersion) {
    if (strategyKey == null || rule == null || input == null) {
      throw new IllegalArgumentException("strategy calculation input is required");
    }
    if (input.hasCurrencyMismatch()) {
      return result(strategyKey, inputVersion, StrategyEvaluationStatus.CROSS_CURRENCY_UNVALUED,
          "策略输入存在不同原币种，未计算比例。", List.of("currency_mismatch"), node -> { });
    }
    if (!input.hasLedgerFacts()) {
      return result(strategyKey, inputVersion, StrategyEvaluationStatus.BLOCKED,
          "等待同策略账本事实。", List.of("strategy_ledger_facts"), node -> { });
    }
    if (!input.hasRequiredInstrumentConfiguration()) {
      return result(strategyKey, inputVersion, StrategyEvaluationStatus.BLOCKED,
          "策略账本事实缺少可验证的标的或合约配置。", List.of("instrument_configuration"), node -> { });
    }
    return switch (strategyKey) {
      case HIGH_DIVIDEND -> highDividend(rule, require(input, HighDividendCalculationInput.class), inputVersion);
      case QQQ_GROWTH -> qqqGrowth(rule, require(input, QqqGrowthCalculationInput.class), inputVersion);
      case IC_IM -> icIm(rule, require(input, IcImCalculationInput.class), inputVersion);
      case DEEP_PUT -> deepPut(rule, require(input, DeepPutCalculationInput.class), inputVersion);
    };
  }

  private StrategyCalculationResult highDividend(JsonNode rule, HighDividendCalculationInput input,
      String inputVersion) {
    long expenseCent = positiveCent(rule, "annual_expense_cent");
    BigDecimal minimumCoverage = decimal(rule, "minimum_dividend_coverage_percent");
    int cashBufferMonths = positiveInteger(rule, "cash_buffer_months");
    BigDecimal coverage = percent(input.trailingIncomeCent(), expenseCent);
    boolean cashEnough = BigDecimal.valueOf(input.cashBufferCent()).multiply(BigDecimal.valueOf(12))
        .compareTo(BigDecimal.valueOf(expenseCent).multiply(BigDecimal.valueOf(cashBufferMonths))) >= 0;
    boolean inRange = coverage.compareTo(minimumCoverage) >= 0 && cashEnough;
    String explanation = inRange ? "近十二个月同策略分红与利息覆盖率及现金垫均满足规则。"
        : "同策略分红覆盖率或现金垫尚未满足规则，保持观察。";
    return result(StrategyKey.HIGH_DIVIDEND, inputVersion, status(inRange), explanation, List.of(), node -> {
      node.put("annual_expense_cent", Long.toString(expenseCent));
      node.put("trailing_income_cent", Long.toString(input.trailingIncomeCent()));
      node.put("income_coverage_percent", number(coverage));
      node.put("cash_buffer_cent", Long.toString(input.cashBufferCent()));
      node.put("required_cash_buffer_months", cashBufferMonths);
    });
  }

  private StrategyCalculationResult qqqGrowth(JsonNode rule, QqqGrowthCalculationInput input, String inputVersion) {
    if (!input.marketInputAvailable() || input.referenceNavCent() <= 0) {
      return result(StrategyKey.QQQ_GROWTH, inputVersion, StrategyEvaluationStatus.DATA_STALE,
          "USD 参考净值或 QQQ/QLD 本地价格快照不可用，未计算配置比例。",
          missing(input.referenceNavCent() <= 0, "reference_nav", !input.marketInputAvailable(), "market_snapshot"),
          node -> { });
    }
    BigDecimal starter = decimal(rule, "starter_percent");
    BigDecimal upper = decimal(rule, "upper_percent");
    BigDecimal qldMaximum = decimal(rule, "qld_max_share_percent");
    long total = Math.addExact(input.qqqMarketValueCent(), input.qldMarketValueCent());
    BigDecimal allocation = percent(total, input.referenceNavCent());
    BigDecimal qldShare = total == 0 ? BigDecimal.ZERO : percent(input.qldMarketValueCent(), total);
    boolean inRange = allocation.compareTo(starter) >= 0 && allocation.compareTo(upper) <= 0
        && qldShare.compareTo(qldMaximum) <= 0;
    String explanation = inRange ? "QQQ/QLD 配置和 QLD 占比均在已确认规则范围内。"
        : "QQQ/QLD 配置或 QLD 占比未满足已确认规则，保持观察。";
    return result(StrategyKey.QQQ_GROWTH, inputVersion, status(inRange), explanation, List.of(), node -> {
      node.put("reference_nav_cent", Long.toString(input.referenceNavCent()));
      node.put("qqq_market_value_cent", Long.toString(input.qqqMarketValueCent()));
      node.put("qld_market_value_cent", Long.toString(input.qldMarketValueCent()));
      node.put("allocation_percent", number(allocation));
      node.put("qld_share_percent", number(qldShare));
      node.put("moving_average_days", positiveInteger(rule, "moving_average_days"));
    });
  }

  private StrategyCalculationResult icIm(JsonNode rule, IcImCalculationInput input, String inputVersion) {
    if (!input.marketInputAvailable()) {
      return result(StrategyKey.IC_IM, inputVersion, StrategyEvaluationStatus.DATA_STALE,
          "PB 分位、贴水或移仓窗口的本地市场快照不可用。", List.of("market_snapshot"), node -> { });
    }
    long minimumPool = positiveCent(rule, "minimum_pool_cent");
    BigDecimal entryPb = decimal(rule, "pb_entry_percentile");
    BigDecimal warningMargin = decimal(rule, "margin_warning_percent");
    int rollWindowDays = positiveInteger(rule, "roll_window_days");
    BigDecimal icPb = new BigDecimal(input.icPbPercentile());
    BigDecimal imPb = new BigDecimal(input.imPbPercentile());
    long totalMargin = Math.addExact(input.availableMarginCent(), input.lockedMarginCent());
    BigDecimal marginRisk = totalMargin == 0 ? BigDecimal.ZERO : percent(input.lockedMarginCent(), totalMargin);
    boolean pbReady = icPb.compareTo(entryPb) <= 0 || imPb.compareTo(entryPb) <= 0;
    boolean rollSafe = input.nearestMaturityDays() == null || input.nearestMaturityDays() > rollWindowDays;
    boolean inRange = input.poolCent() >= minimumPool && pbReady && marginRisk.compareTo(warningMargin) <= 0 && rollSafe;
    String explanation = inRange ? "资金池、PB 分位、保证金风险和移仓窗口均满足规则。"
        : "资金池、PB 分位、保证金风险或移仓窗口未满足规则，保持观察。";
    return result(StrategyKey.IC_IM, inputVersion, status(inRange), explanation, List.of(), node -> {
      node.put("pool_cent", Long.toString(input.poolCent()));
      node.put("available_margin_cent", Long.toString(input.availableMarginCent()));
      node.put("locked_margin_cent", Long.toString(input.lockedMarginCent()));
      node.put("margin_risk_percent", number(marginRisk));
      node.put("ic_pb_percentile", number(icPb));
      node.put("im_pb_percentile", number(imPb));
      node.put("ic_annualized_basis", input.icAnnualizedBasis());
      node.put("im_annualized_basis", input.imAnnualizedBasis());
      if (input.nearestMaturityDays() != null) {
        node.put("nearest_maturity_days", input.nearestMaturityDays());
      }
    });
  }

  private StrategyCalculationResult deepPut(JsonNode rule, DeepPutCalculationInput input, String inputVersion) {
    if (!input.referenceNavAvailable() || input.referenceNavCent() <= 0) {
      return result(StrategyKey.DEEP_PUT, inputVersion, StrategyEvaluationStatus.DATA_STALE,
          "USD 策略参考净值不可用，未计算保险预算比例。", List.of("reference_nav"), node -> { });
    }
    BigDecimal minimumBudget = decimal(rule, "budget_min_percent");
    BigDecimal maximumBudget = decimal(rule, "budget_max_percent");
    int expiryWarningDays = positiveInteger(rule, "expiry_warning_days");
    BigDecimal budget = percent(input.trailingPremiumCent(), input.referenceNavCent());
    LocalDate warningDate = input.asOfDate().plusDays(expiryWarningDays);
    boolean expirySafe = input.nearestExpiryDate() != null && input.nearestExpiryDate().isAfter(warningDate);
    boolean inRange = input.openPutQuantity().signum() > 0 && budget.compareTo(minimumBudget) >= 0
        && budget.compareTo(maximumBudget) <= 0 && expirySafe;
    String explanation = inRange ? "保险预算和最近到期梯度均满足规则。"
        : "保险预算、在持 Put 或到期梯度未满足规则，保持观察。";
    return result(StrategyKey.DEEP_PUT, inputVersion, status(inRange), explanation, List.of(), node -> {
      node.put("reference_nav_cent", Long.toString(input.referenceNavCent()));
      node.put("trailing_premium_cent", Long.toString(input.trailingPremiumCent()));
      node.put("premium_budget_percent", number(budget));
      node.put("open_put_quantity", number(input.openPutQuantity()));
      if (input.nearestExpiryDate() != null) {
        node.put("nearest_expiry_date", input.nearestExpiryDate().toString());
      }
    });
  }

  private StrategyCalculationResult result(StrategyKey key, String inputVersion, StrategyEvaluationStatus status,
      String explanation, List<String> missing, NodeWriter writer) {
    try {
      ObjectNode root = json.createObjectNode();
      root.put("currency", key.currency().name());
      root.put("inputVersion", inputVersion == null ? "" : inputVersion);
      ArrayNode missingFields = root.putArray("missingOrStaleFields");
      missing.forEach(missingFields::add);
      ObjectNode signal = root.putArray("signals").addObject();
      signal.put("signalType", status.name());
      signal.put("explanation", explanation);
      writer.write(root);
      return new StrategyCalculationResult(status, json.writeValueAsString(root), explanation);
    } catch (Exception exception) {
      throw new IllegalStateException("strategy calculation result could not be encoded", exception);
    }
  }

  private static StrategyEvaluationStatus status(boolean inRange) {
    return inRange ? StrategyEvaluationStatus.IN_RANGE : StrategyEvaluationStatus.WATCH;
  }

  private static <T extends StrategyCalculationInput> T require(StrategyCalculationInput input, Class<T> type) {
    if (!type.isInstance(input)) {
      throw new IllegalArgumentException("strategy calculation input does not match its strategy key");
    }
    return type.cast(input);
  }

  private static long positiveCent(JsonNode rule, String field) {
    return Long.parseLong(text(rule, field));
  }

  private static int positiveInteger(JsonNode rule, String field) {
    return Integer.parseInt(text(rule, field));
  }

  private static BigDecimal decimal(JsonNode rule, String field) {
    return new BigDecimal(text(rule, field));
  }

  private static String text(JsonNode rule, String field) {
    JsonNode value = rule.get(field);
    if (value == null || !value.isTextual()) {
      throw new IllegalArgumentException("strategy rule field is absent: " + field);
    }
    return value.textValue();
  }

  private static BigDecimal percent(long numerator, long denominator) {
    if (denominator <= 0) {
      throw new IllegalArgumentException("ratio denominator must be positive");
    }
    return BigDecimal.valueOf(numerator).multiply(HUNDRED).divide(BigDecimal.valueOf(denominator), 4,
        RoundingMode.HALF_UP);
  }

  private static String number(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private static List<String> missing(boolean first, String firstName, boolean second, String secondName) {
    if (first && second) {
      return List.of(firstName, secondName);
    }
    if (first) {
      return List.of(firstName);
    }
    return second ? List.of(secondName) : List.of();
  }

  @FunctionalInterface
  private interface NodeWriter {
    void write(ObjectNode node);
  }
}
