package com.personal.investment.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.market.domain.InstrumentStatus;
import com.personal.investment.market.domain.OptionRight;
import com.personal.investment.market.domain.OptionSpecification;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortfolioOverviewServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String CNY_CASH = "01K8D43J4YFN7X9R2B6C8M0V3A";
  private static final String USD_CASH_A = "01K8D43J4YFN7X9R2B6C8M0V3B";
  private static final String USD_CASH_B = "01K8D43J4YFN7X9R2B6C8M0V3C";
  private static final String EQUITY = "01K8D43J4YFN7X9R2B6C8M0V3E";
  private static final String OPTION = "01K8D43J4YFN7X9R2B6C8M0V3O";
  private static final String FUTURE = "01K8D43J4YFN7X9R2B6C8M0V3F";
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void countsAUserInstrumentTotalMarketValueOnceAndNeverAllocatesItAcrossCashAccounts() {
    Fixture fixture = new Fixture();
    fixture.accounts.add(new PortfolioAccountBalance(USD_CASH_A, CurrencyCode.USD, 1_000L, 100L));
    fixture.accounts.add(new PortfolioAccountBalance(USD_CASH_B, CurrencyCode.USD, 2_000L, 200L));
    fixture.positions.add(position(USD_CASH_A, EQUITY, CurrencyCode.USD, "2"));
    fixture.positions.add(position(USD_CASH_B, EQUITY, CurrencyCode.USD, "3"));
    fixture.valuations.add(total(EQUITY, CurrencyCode.USD, 9_999L, "2026-07-28", null));

    PortfolioCurrencyOverview overview = fixture.service().summary(OWNER, LocalDate.of(2026, 7, 29)).items().getFirst();

    assertThat(overview.cashCent()).isEqualTo(3_000L);
    assertThat(overview.marginCent()).isEqualTo(300L);
    assertThat(overview.marketValueCent()).isEqualTo(9_999L);
    assertThat(overview.netAssetCent()).isEqualTo(13_299L);
    assertThat(overview.valuationStatus()).isEqualTo(PortfolioValuationStatus.MANUAL);
    assertThat(overview.positions()).allSatisfy(position -> {
      assertThat(position.marketValueCent()).isNull();
      assertThat(position.valuationStatus()).isEqualTo(PortfolioPositionValuationStatus.MANUAL_TOTAL_UNALLOCATED);
    });
  }

  @Test
  void valuesLongOptionsUsingTheImmutableContractMultiplierAndReturnsAccountValuesOnlyForUnitPrices() {
    Fixture fixture = new Fixture();
    fixture.accounts.add(new PortfolioAccountBalance(USD_CASH_A, CurrencyCode.USD, 1_000L, 0L));
    fixture.positions.add(position(USD_CASH_A, OPTION, CurrencyCode.USD, "2"));
    fixture.valuations.add(unit(OPTION, CurrencyCode.USD, 250L, "2026-07-29", null));

    PortfolioCurrencyOverview overview = fixture.service().summary(OWNER, LocalDate.of(2026, 7, 29)).items().getFirst();

    assertThat(overview.marketValueCent()).isEqualTo(50_000L);
    assertThat(overview.netAssetCent()).isEqualTo(51_000L);
    assertThat(overview.positions()).singleElement().satisfies(position -> {
      assertThat(position.marketValueCent()).isEqualTo(50_000L);
      assertThat(position.valuationStatus()).isEqualTo(PortfolioPositionValuationStatus.MANUAL_UNIT_PRICE);
    });
  }

  @Test
  void exposesUnrealizedPnlOnlyWhenFifoCostAndUnitPriceValuationAreBothExact() {
    Fixture fixture = new Fixture();
    fixture.accounts.add(new PortfolioAccountBalance(USD_CASH_A, CurrencyCode.USD, 0L, 0L));
    fixture.positions.add(new PortfolioOpenPosition(USD_CASH_A, EQUITY, CurrencyCode.USD,
        new BigDecimal("2"), 2_005L));
    fixture.valuations.add(unit(EQUITY, CurrencyCode.USD, 1_500L, "2026-07-29", null));

    PortfolioPositionView position = fixture.service().summary(OWNER, LocalDate.of(2026, 7, 29)).items().getFirst()
        .positions().getFirst();

    assertThat(position.costCent()).isEqualTo(2_005L);
    assertThat(position.marketValueCent()).isEqualTo(3_000L);
    assertThat(position.unrealizedPnlCent()).isEqualTo(995L);
  }

  @Test
  void ignoresNewerExpiredValuationWhenAnOlderUnexpiredValuationIsAvailable() {
    Fixture fixture = new Fixture();
    fixture.accounts.add(new PortfolioAccountBalance(USD_CASH_A, CurrencyCode.USD, 0L, 0L));
    fixture.positions.add(position(USD_CASH_A, EQUITY, CurrencyCode.USD, "2"));
    fixture.valuations.add(unit(EQUITY, CurrencyCode.USD, 300L, "2026-07-27", null));
    fixture.valuations.add(unit(EQUITY, CurrencyCode.USD, 500L, "2026-07-28", "2026-07-29T11:59:59Z"));

    PortfolioCurrencyOverview overview = fixture.service().summary(OWNER, LocalDate.of(2026, 7, 29)).items().getFirst();

    assertThat(overview.marketValueCent()).isEqualTo(600L);
    assertThat(overview.positions()).singleElement().extracting(PortfolioPositionView::valuationStatus)
        .isEqualTo(PortfolioPositionValuationStatus.MANUAL_UNIT_PRICE);
  }

  @Test
  void returnsNullPortfolioTotalsWhenAnyOpenPositionIsUnvaluedAndMarksFuturesForSettlementOnly() {
    Fixture fixture = new Fixture();
    fixture.accounts.add(new PortfolioAccountBalance(CNY_CASH, CurrencyCode.CNY, 10_000L, 30_000L));
    fixture.positions.add(position(CNY_CASH, FUTURE, CurrencyCode.CNY, "1"));

    PortfolioCurrencyOverview overview = fixture.service().summary(OWNER, LocalDate.of(2026, 7, 29)).items().getFirst();

    assertThat(overview.marketValueCent()).isNull();
    assertThat(overview.netAssetCent()).isNull();
    assertThat(overview.valuationStatus()).isEqualTo(PortfolioValuationStatus.UNVALUED);
    assertThat(overview.positions()).singleElement().extracting(PortfolioPositionView::valuationStatus)
        .isEqualTo(PortfolioPositionValuationStatus.FUTURES_SETTLEMENT_ONLY);
  }

  @Test
  void neverRoundsFractionalCentUnitPriceValuations() {
    Fixture fixture = new Fixture();
    fixture.accounts.add(new PortfolioAccountBalance(USD_CASH_A, CurrencyCode.USD, 0L, 0L));
    fixture.positions.add(position(USD_CASH_A, EQUITY, CurrencyCode.USD, "0.1"));
    fixture.valuations.add(unit(EQUITY, CurrencyCode.USD, 1L, "2026-07-29", null));

    PortfolioCurrencyOverview overview = fixture.service().summary(OWNER, LocalDate.of(2026, 7, 29)).items().getFirst();

    assertThat(overview.marketValueCent()).isNull();
    assertThat(overview.netAssetCent()).isNull();
    assertThat(overview.positions()).singleElement().extracting(PortfolioPositionView::valuationStatus)
        .isEqualTo(PortfolioPositionValuationStatus.PRECISION_UNAVAILABLE);
  }

  private static PortfolioOpenPosition position(String cashAccountId, String instrumentId, CurrencyCode currency,
      String quantity) {
    return new PortfolioOpenPosition(cashAccountId, instrumentId, currency, new BigDecimal(quantity));
  }

  private static PortfolioManualValuation unit(String instrumentId, CurrencyCode currency, long unitPriceCent,
      String valuationDate, String validUntil) {
    return new PortfolioManualValuation(instrumentId, currency, LocalDate.parse(valuationDate), unitPriceCent,
        null, (short) 100, validUntil == null ? null : Instant.parse(validUntil), LocalDateTime.parse("2026-07-29T00:00:00"));
  }

  private static PortfolioManualValuation total(String instrumentId, CurrencyCode currency, long marketValueCent,
      String valuationDate, String validUntil) {
    return new PortfolioManualValuation(instrumentId, currency, LocalDate.parse(valuationDate), null, marketValueCent,
        (short) 100, validUntil == null ? null : Instant.parse(validUntil), LocalDateTime.parse("2026-07-29T00:00:00"));
  }

  private static final class Fixture {
    private final List<PortfolioAccountBalance> accounts = new ArrayList<>();
    private final List<PortfolioOpenPosition> positions = new ArrayList<>();
    private final List<PortfolioManualValuation> valuations = new ArrayList<>();

    private PortfolioOverviewService service() {
      return new PortfolioOverviewService(new PortfolioOverviewPort() {
        @Override
        public List<PortfolioAccountBalance> findAccountBalances(String ownerUserId, LocalDate asOf) {
          return accounts;
        }

        @Override
        public List<PortfolioOpenPosition> findOpenPositions(String ownerUserId, LocalDate asOf) {
          return positions;
        }

        @Override
        public List<PortfolioManualValuation> findManualValuations(String ownerUserId, LocalDate asOf) {
          return valuations;
        }

        @Override
        public long currentLedgerVersion(String ownerUserId) {
          return 42L;
        }
      }, instruments(), CLOCK);
    }
  }

  private static InstrumentPort instruments() {
    Instrument equity = new Instrument(EQUITY, "US", "NASDAQ", "TEST", "测试股票", AssetType.EQUITY,
        CurrencyCode.USD, null, InstrumentStatus.ACTIVE, null, null);
    Instrument option = new Instrument(OPTION, "US", "OPRA", "TESTP", "测试期权", AssetType.OPTION,
        CurrencyCode.USD, LocalDate.of(2026, 12, 18), InstrumentStatus.ACTIVE, null,
        new OptionSpecification(EQUITY, OptionRight.PUT, 500L, 100L));
    Instrument future = new Instrument(FUTURE, "CFFEX", "CFFEX", "IC2608", "测试期货", AssetType.FUTURE,
        CurrencyCode.CNY, LocalDate.of(2026, 8, 21), InstrumentStatus.ACTIVE,
        new com.personal.investment.market.domain.FutureSpecification("IC", 20000L), null);
    return new InstrumentPort() {
      @Override
      public Optional<Instrument> findByNaturalKey(String market, String exchange, String symbol) {
        return Optional.empty();
      }

      @Override
      public Optional<Instrument> findById(String instrumentId) {
        return List.of(equity, option, future).stream().filter(value -> value.instrumentId().equals(instrumentId))
            .findFirst();
      }

      @Override
      public void insert(Instrument instrument, String futureContractId, String optionContractId) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
