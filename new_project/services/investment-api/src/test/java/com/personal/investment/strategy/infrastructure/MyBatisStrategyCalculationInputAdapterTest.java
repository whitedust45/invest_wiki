package com.personal.investment.strategy.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.strategy.application.DeepPutCalculationInput;
import com.personal.investment.strategy.application.IcImCalculationInput;
import com.personal.investment.strategy.application.QqqGrowthCalculationInput;
import com.personal.investment.strategy.application.StrategyReferenceNav;
import com.personal.investment.strategy.domain.StrategyKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisStrategyCalculationInputAdapterTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final Instant AS_OF = Instant.parse("2026-07-31T00:00:00Z");

  @Test
  void marksQqqMarketInputUnavailableWhenQuantityTimesQuoteCannotBeExpressedInMinorUnits() {
    StubMapper mapper = new StubMapper();
    mapper.ledger = new StrategyCalculationReadMapper.LedgerSummaryRow(7L, 1L, 0, 0L);
    mapper.spotPositions = List.of(new StrategyCalculationReadMapper.SpotPositionRow(
        "01K8D43J4YFN7X9R2B6C8M0V01", "QQQ", new BigDecimal("0.5")));
    mapper.marketRun = new StrategyCalculationReadMapper.MarketSyncRunRow("01K8D43J4YFN7X9R2B6C8M0V02",
        LocalDate.of(2026, 7, 31), AS_OF);
    mapper.quotes = List.of(new StrategyCalculationReadMapper.QuoteRow("QQQ", "01K8D43J4YFN7X9R2B6C8M0V03",
        1001L, "USD"));

    QqqGrowthCalculationInput input = (QqqGrowthCalculationInput) new MyBatisStrategyCalculationInputAdapter(mapper)
        .load(OWNER, StrategyKey.QQQ_GROWTH, AS_OF, referenceNav());

    assertThat(input.marketInputAvailable()).isFalse();
    assertThat(input.qqqMarketValueCent()).isZero();
    assertThat(input.inputVersion()).contains("ledger:7", "market:01K8D43J4YFN7X9R2B6C8M0V02",
        "reference:01K8D43J4YFN7X9R2B6C8M0V04");
  }

  @Test
  void buildsIcImPoolOnlyFromStrategyAttributedCashAndMarginAndPinsMarketInputToOneRun() {
    StubMapper mapper = new StubMapper();
    LocalDate date = LocalDate.of(2026, 7, 31);
    mapper.ledger = new StrategyCalculationReadMapper.LedgerSummaryRow(12L, 4L, 0, 0L);
    mapper.margin = new StrategyCalculationReadMapper.MarginSummaryRow(70_000_000L, 20_000_000L, 10_000_000L);
    mapper.products = List.of("IC", "IM");
    mapper.marketRun = new StrategyCalculationReadMapper.MarketSyncRunRow("01K8D43J4YFN7X9R2B6C8M0V05", date, AS_OF);
    mapper.pbMetrics = List.of(
        new StrategyCalculationReadMapper.PbMetricRow("IC", "01K8D43J4YFN7X9R2B6C8M0V06", new BigDecimal("28.40")),
        new StrategyCalculationReadMapper.PbMetricRow("IM", "01K8D43J4YFN7X9R2B6C8M0V07", new BigDecimal("44.20")));
    mapper.basis = List.of(
        new StrategyCalculationReadMapper.BasisRow("IC", "01K8D43J4YFN7X9R2B6C8M0V08", new BigDecimal("-0.0825")),
        new StrategyCalculationReadMapper.BasisRow("IM", "01K8D43J4YFN7X9R2B6C8M0V09", new BigDecimal("-0.0741")));
    mapper.futuresPositions = List.of(new StrategyCalculationReadMapper.FuturesPositionRow("IC",
        LocalDate.of(2026, 8, 18), BigDecimal.ONE));

    IcImCalculationInput input = (IcImCalculationInput) new MyBatisStrategyCalculationInputAdapter(mapper)
        .load(OWNER, StrategyKey.IC_IM, AS_OF, null);

    assertThat(input.hasRequiredInstrumentConfiguration()).isTrue();
    assertThat(input.marketInputAvailable()).isTrue();
    assertThat(input.poolCent()).isEqualTo(100_000_000L);
    assertThat(input.icPbPercentile()).isEqualTo("28.4");
    assertThat(input.imAnnualizedBasis()).isEqualTo("-0.0741");
    assertThat(input.nearestMaturityDays()).isEqualTo(18);
    assertThat(input.inputVersion()).contains("market:01K8D43J4YFN7X9R2B6C8M0V05");
  }

  @Test
  void calculatesDeepPutPremiumFromOpenFactsWithoutFloatingPointRounding() {
    StubMapper mapper = new StubMapper();
    LocalDate date = LocalDate.of(2026, 7, 31);
    mapper.ledger = new StrategyCalculationReadMapper.LedgerSummaryRow(6L, 2L, 0, 0L);
    mapper.optionTrades = List.of(
        new StrategyCalculationReadMapper.OptionTradeRow("OPTION_OPEN", LocalDate.of(2026, 2, 1),
            "01K8D43J4YFN7X9R2B6C8M0V10", BigDecimal.ONE, 12L, 99L, 100L, LocalDate.of(2026, 9, 18), "PUT", "USD"),
        new StrategyCalculationReadMapper.OptionTradeRow("OPTION_OPEN", LocalDate.of(2026, 4, 1),
            "01K8D43J4YFN7X9R2B6C8M0V11", BigDecimal.ONE, 8L, 1L, 100L, LocalDate.of(2026, 11, 20), "PUT", "USD"));

    DeepPutCalculationInput input = (DeepPutCalculationInput) new MyBatisStrategyCalculationInputAdapter(mapper)
        .load(OWNER, StrategyKey.DEEP_PUT, AS_OF, referenceNav());

    assertThat(input.hasRequiredInstrumentConfiguration()).isTrue();
    assertThat(input.trailingPremiumCent()).isEqualTo(2_100L);
    assertThat(input.openPutQuantity()).isEqualByComparingTo("2");
    assertThat(input.nearestExpiryDate()).isEqualTo(LocalDate.of(2026, 9, 18));
  }

  private static StrategyReferenceNav referenceNav() {
    return new StrategyReferenceNav("01K8D43J4YFN7X9R2B6C8M0V04", OWNER, StrategyKey.QQQ_GROWTH, CurrencyCode.USD,
        100_000L, AS_OF, AS_OF.plusSeconds(86_400L), "MANUAL", AS_OF);
  }

  private static final class StubMapper implements StrategyCalculationReadMapper {
    private StrategyCalculationReadMapper.LedgerSummaryRow ledger;
    private StrategyCalculationReadMapper.MarginSummaryRow margin;
    private List<StrategyCalculationReadMapper.SpotPositionRow> spotPositions = List.of();
    private StrategyCalculationReadMapper.MarketSyncRunRow marketRun;
    private List<StrategyCalculationReadMapper.QuoteRow> quotes = List.of();
    private List<String> products = List.of();
    private List<StrategyCalculationReadMapper.PbMetricRow> pbMetrics = List.of();
    private List<StrategyCalculationReadMapper.BasisRow> basis = List.of();
    private List<StrategyCalculationReadMapper.FuturesPositionRow> futuresPositions = List.of();
    private List<StrategyCalculationReadMapper.OptionTradeRow> optionTrades = List.of();

    @Override
    public StrategyCalculationReadMapper.LedgerSummaryRow findLedgerSummary(String ownerUserId, String strategyKey,
        String currency, LocalDate asOfDate) {
      return ledger;
    }

    @Override
    public long findTrailingNetIncome(String ownerUserId, String strategyKey, LocalDate asOfDate) {
      return 0L;
    }

    @Override
    public StrategyCalculationReadMapper.MarginSummaryRow findMarginSummary(String ownerUserId, String strategyKey,
        LocalDate asOfDate) {
      return margin;
    }

    @Override
    public List<StrategyCalculationReadMapper.SpotPositionRow> findQqqPositions(String ownerUserId,
        String strategyKey, LocalDate asOfDate) {
      return spotPositions;
    }

    @Override
    public StrategyCalculationReadMapper.MarketSyncRunRow findLatestSucceededMarketRun(LocalDate asOfDate) {
      return marketRun;
    }

    @Override
    public List<StrategyCalculationReadMapper.QuoteRow> findQqqQuotesForRun(String marketSyncRunId) {
      return quotes;
    }

    @Override
    public List<String> findFuturesProducts(String ownerUserId, String strategyKey, LocalDate asOfDate) {
      return products;
    }

    @Override
    public List<StrategyCalculationReadMapper.PbMetricRow> findPbMetricsForRun(String marketSyncRunId) {
      return pbMetrics;
    }

    @Override
    public List<StrategyCalculationReadMapper.BasisRow> findBasisForRun(String marketSyncRunId) {
      return basis;
    }

    @Override
    public List<StrategyCalculationReadMapper.FuturesPositionRow> findFuturesPositions(String ownerUserId,
        String strategyKey, LocalDate asOfDate) {
      return futuresPositions;
    }

    @Override
    public List<StrategyCalculationReadMapper.OptionTradeRow> findOptionTrades(String ownerUserId,
        String strategyKey, LocalDate asOfDate) {
      return optionTrades;
    }
  }
}
