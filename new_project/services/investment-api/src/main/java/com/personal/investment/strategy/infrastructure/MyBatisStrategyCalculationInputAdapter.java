package com.personal.investment.strategy.infrastructure;

import com.personal.investment.strategy.application.DeepPutCalculationInput;
import com.personal.investment.strategy.application.HighDividendCalculationInput;
import com.personal.investment.strategy.application.IcImCalculationInput;
import com.personal.investment.strategy.application.QqqGrowthCalculationInput;
import com.personal.investment.strategy.application.StrategyCalculationInput;
import com.personal.investment.strategy.application.StrategyCalculationInputPort;
import com.personal.investment.strategy.application.StrategyReferenceNav;
import com.personal.investment.strategy.domain.StrategyKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Assembles calculation inputs solely from strategy-attributed ledger facts and completed local market runs. */
@Component
public class MyBatisStrategyCalculationInputAdapter implements StrategyCalculationInputPort {
  private static final String IC = "IC";
  private static final String IM = "IM";
  private final StrategyCalculationReadMapper mapper;

  public MyBatisStrategyCalculationInputAdapter(StrategyCalculationReadMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public StrategyCalculationInput load(String ownerUserId, StrategyKey strategyKey, Instant asOfAt,
      StrategyReferenceNav referenceNav) {
    LocalDate asOfDate = asOfAt.atZone(ZoneOffset.UTC).toLocalDate();
    return switch (strategyKey) {
      case HIGH_DIVIDEND -> highDividend(ownerUserId, asOfDate);
      case QQQ_GROWTH -> qqqGrowth(ownerUserId, asOfDate, referenceNav);
      case IC_IM -> icIm(ownerUserId, asOfDate);
      case DEEP_PUT -> deepPut(ownerUserId, asOfDate, referenceNav);
    };
  }

  private HighDividendCalculationInput highDividend(String ownerUserId, LocalDate asOfDate) {
    StrategyCalculationReadMapper.LedgerSummaryRow ledger = ledger(ownerUserId, StrategyKey.HIGH_DIVIDEND, asOfDate);
    long income = mapper.findTrailingNetIncome(ownerUserId, StrategyKey.HIGH_DIVIDEND.name(), asOfDate);
    return new HighDividendCalculationInput(version(ledger.ledgerVersion(), "N/A", "N/A"),
        ledger.transactionCount() > 0, ledger.currencyMismatch() != 0, ledger.cashBalanceCent(), income);
  }

  private QqqGrowthCalculationInput qqqGrowth(String ownerUserId, LocalDate asOfDate,
      StrategyReferenceNav referenceNav) {
    StrategyCalculationReadMapper.LedgerSummaryRow ledger = ledger(ownerUserId, StrategyKey.QQQ_GROWTH, asOfDate);
    Map<String, BigDecimal> quantities = new HashMap<>();
    mapper.findQqqPositions(ownerUserId, StrategyKey.QQQ_GROWTH.name(), asOfDate)
        .forEach(row -> quantities.merge(row.symbol(), row.netQuantity(), BigDecimal::add));
    boolean needsQqqQuote = nonZero(quantities.get("QQQ"));
    boolean needsQldQuote = nonZero(quantities.get("QLD"));
    StrategyCalculationReadMapper.MarketSyncRunRow run = needsQqqQuote || needsQldQuote
        ? mapper.findLatestSucceededMarketRun(asOfDate) : null;
    Map<String, StrategyCalculationReadMapper.QuoteRow> quotes = run == null ? Map.of()
        : bySymbol(mapper.findQqqQuotesForRun(run.marketSyncRunId()));
    boolean quoteCurrencyMismatch = (needsQqqQuote && quotes.containsKey("QQQ")
        && !"USD".equals(quotes.get("QQQ").currency())) || (needsQldQuote && quotes.containsKey("QLD")
        && !"USD".equals(quotes.get("QLD").currency()));
    ExactValue qqq = marketValue(quantities.get("QQQ"), quotes.get("QQQ"));
    ExactValue qld = marketValue(quantities.get("QLD"), quotes.get("QLD"));
    boolean marketAvailable = (!needsQqqQuote || qqq.available()) && (!needsQldQuote || qld.available());
    String marketVersion = run == null ? "N/A" : run.marketSyncRunId() + ":"
        + joinedIds(quotes.values().stream().map(StrategyCalculationReadMapper.QuoteRow::quoteSnapshotId).toList());
    return new QqqGrowthCalculationInput(version(ledger.ledgerVersion(), marketVersion, referenceVersion(referenceNav)),
        ledger.transactionCount() > 0, ledger.currencyMismatch() != 0 || quoteCurrencyMismatch, marketAvailable,
        referenceNav == null ? 0L : referenceNav.referenceNavCent(), qqq.valueCent(), qld.valueCent());
  }

  private IcImCalculationInput icIm(String ownerUserId, LocalDate asOfDate) {
    StrategyCalculationReadMapper.LedgerSummaryRow ledger = ledger(ownerUserId, StrategyKey.IC_IM, asOfDate);
    StrategyCalculationReadMapper.MarginSummaryRow margin = mapper.findMarginSummary(ownerUserId, StrategyKey.IC_IM.name(),
        asOfDate);
    List<String> products = mapper.findFuturesProducts(ownerUserId, StrategyKey.IC_IM.name(), asOfDate);
    boolean configured = products.contains(IC) && products.contains(IM);
    StrategyCalculationReadMapper.MarketSyncRunRow run = configured ? mapper.findLatestSucceededMarketRun(asOfDate) : null;
    Map<String, StrategyCalculationReadMapper.PbMetricRow> pb = run == null ? Map.of()
        : byProductPb(mapper.findPbMetricsForRun(run.marketSyncRunId()));
    Map<String, StrategyCalculationReadMapper.BasisRow> basis = run == null ? Map.of()
        : byProductBasis(mapper.findBasisForRun(run.marketSyncRunId()));
    boolean marketAvailable = run != null && pb.containsKey(IC) && pb.containsKey(IM)
        && basis.containsKey(IC) && basis.containsKey(IM);
    long poolCent = add(margin.cashCent(), margin.availableMarginCent(), margin.lockedMarginCent());
    Integer nearestMaturityDays = mapper.findFuturesPositions(ownerUserId, StrategyKey.IC_IM.name(), asOfDate).stream()
        .filter(value -> value.netQuantity().signum() > 0).map(StrategyCalculationReadMapper.FuturesPositionRow::maturityDate)
        .filter(java.util.Objects::nonNull).map(value -> Math.toIntExact(ChronoUnit.DAYS.between(asOfDate, value)))
        .min(Comparator.naturalOrder()).orElse(null);
    String marketVersion = run == null ? "NONE" : run.marketSyncRunId() + ":"
        + joinedIds(pb.values().stream().map(StrategyCalculationReadMapper.PbMetricRow::dailyMetricId).toList()) + ":"
        + joinedIds(basis.values().stream().map(StrategyCalculationReadMapper.BasisRow::basisSnapshotId).toList());
    return new IcImCalculationInput(version(ledger.ledgerVersion(), marketVersion, "N/A"),
        ledger.transactionCount() > 0, ledger.currencyMismatch() != 0, configured, marketAvailable, poolCent,
        margin.availableMarginCent(), margin.lockedMarginCent(), decimal(pb.get(IC)), decimal(pb.get(IM)),
        decimalBasis(basis.get(IC)), decimalBasis(basis.get(IM)), nearestMaturityDays);
  }

  private DeepPutCalculationInput deepPut(String ownerUserId, LocalDate asOfDate, StrategyReferenceNav referenceNav) {
    StrategyCalculationReadMapper.LedgerSummaryRow ledger = ledger(ownerUserId, StrategyKey.DEEP_PUT, asOfDate);
    Map<String, BigDecimal> openQuantity = new HashMap<>();
    long[] premium = {0L};
    boolean[] configured = {true};
    boolean[] crossCurrency = {ledger.currencyMismatch() != 0};
    LocalDate trailingStart = asOfDate.minusYears(1);
    Map<String, LocalDate> maturities = new HashMap<>();
    for (StrategyCalculationReadMapper.OptionTradeRow row : mapper.findOptionTrades(ownerUserId, StrategyKey.DEEP_PUT.name(),
        asOfDate)) {
      if (!"PUT".equals(row.optionRight()) || !"USD".equals(row.currency()) || row.maturityDate() == null) {
        configured[0] = false;
      }
      if (!"USD".equals(row.currency())) {
        crossCurrency[0] = true;
      }
      maturities.put(row.instrumentId(), row.maturityDate());
      BigDecimal direction = "OPTION_OPEN".equals(row.transactionType()) ? BigDecimal.ONE : BigDecimal.ONE.negate();
      openQuantity.merge(row.instrumentId(), row.quantity().multiply(direction), BigDecimal::add);
      if ("OPTION_OPEN".equals(row.transactionType()) && !row.occurredOn().isBefore(trailingStart)) {
        if (row.unitPriceCent() == null || row.optionContractMultiplier() == null) {
          configured[0] = false;
        } else {
          premium[0] = add(premium[0], exactPremium(row.quantity(), row.optionContractMultiplier(), row.unitPriceCent()),
              row.feeCent());
        }
      }
    }
    if (openQuantity.values().stream().anyMatch(value -> value.signum() < 0)) {
      configured[0] = false;
    }
    BigDecimal totalOpen = openQuantity.values().stream().filter(value -> value.signum() > 0)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    LocalDate nearestExpiry = openQuantity.entrySet().stream().filter(value -> value.getValue().signum() > 0)
        .map(value -> maturities.get(value.getKey())).filter(java.util.Objects::nonNull).min(Comparator.naturalOrder())
        .orElse(null);
    return new DeepPutCalculationInput(version(ledger.ledgerVersion(), "N/A", referenceVersion(referenceNav)),
        ledger.transactionCount() > 0, crossCurrency[0], configured[0], referenceNav != null,
        referenceNav == null ? 0L : referenceNav.referenceNavCent(), premium[0], totalOpen, asOfDate, nearestExpiry);
  }

  private StrategyCalculationReadMapper.LedgerSummaryRow ledger(String ownerUserId, StrategyKey strategyKey,
      LocalDate asOfDate) {
    return mapper.findLedgerSummary(ownerUserId, strategyKey.name(), strategyKey.currency().name(), asOfDate);
  }

  private static Map<String, StrategyCalculationReadMapper.QuoteRow> bySymbol(
      List<StrategyCalculationReadMapper.QuoteRow> values) {
    Map<String, StrategyCalculationReadMapper.QuoteRow> result = new HashMap<>();
    values.forEach(value -> result.put(value.symbol(), value));
    return result;
  }

  private static Map<String, StrategyCalculationReadMapper.PbMetricRow> byProductPb(
      List<StrategyCalculationReadMapper.PbMetricRow> values) {
    Map<String, StrategyCalculationReadMapper.PbMetricRow> result = new HashMap<>();
    values.forEach(value -> result.put(value.productCode(), value));
    return result;
  }

  private static Map<String, StrategyCalculationReadMapper.BasisRow> byProductBasis(
      List<StrategyCalculationReadMapper.BasisRow> values) {
    Map<String, StrategyCalculationReadMapper.BasisRow> result = new HashMap<>();
    values.forEach(value -> result.put(value.productCode(), value));
    return result;
  }

  private static ExactValue marketValue(BigDecimal quantity, StrategyCalculationReadMapper.QuoteRow quote) {
    if (!nonZero(quantity)) {
      return new ExactValue(true, 0L);
    }
    if (quote == null) {
      return new ExactValue(false, 0L);
    }
    try {
      return new ExactValue(true, quantity.multiply(BigDecimal.valueOf(quote.priceCent())).setScale(0,
          RoundingMode.UNNECESSARY).longValueExact());
    } catch (ArithmeticException exception) {
      return new ExactValue(false, 0L);
    }
  }

  private static long exactPremium(BigDecimal quantity, long multiplier, long unitPriceCent) {
    try {
      return quantity.multiply(BigDecimal.valueOf(multiplier)).multiply(BigDecimal.valueOf(unitPriceCent))
          .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("persisted option premium is not representable in minor units", exception);
    }
  }

  private static long add(long... values) {
    long total = 0L;
    for (long value : values) {
      total = Math.addExact(total, value);
    }
    return total;
  }

  private static boolean nonZero(BigDecimal value) {
    return value != null && value.signum() != 0;
  }

  private static String decimal(StrategyCalculationReadMapper.PbMetricRow value) {
    return value == null ? null : value.metricValueDecimal().stripTrailingZeros().toPlainString();
  }

  private static String decimalBasis(StrategyCalculationReadMapper.BasisRow value) {
    return value == null ? null : value.annualizedBasis().stripTrailingZeros().toPlainString();
  }

  private static String version(long ledgerVersion, String marketVersion, String referenceVersion) {
    return "ledger:" + ledgerVersion + "|market:" + marketVersion + "|reference:" + referenceVersion;
  }

  private static String referenceVersion(StrategyReferenceNav referenceNav) {
    return referenceNav == null ? "NONE" : referenceNav.strategyReferenceNavId();
  }

  private static String joinedIds(List<String> ids) {
    return ids.stream().sorted().reduce((left, right) -> left + "," + right).orElse("NONE");
  }

  private record ExactValue(boolean available, long valueCent) {
  }
}
