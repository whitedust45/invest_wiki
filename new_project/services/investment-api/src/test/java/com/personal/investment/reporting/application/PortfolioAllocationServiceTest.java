package com.personal.investment.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.portfolio.application.PortfolioAccountBalance;
import com.personal.investment.portfolio.application.PortfolioManualValuation;
import com.personal.investment.portfolio.application.PortfolioOpenPosition;
import com.personal.investment.portfolio.application.PortfolioOverviewPort;
import com.personal.investment.portfolio.application.PortfolioOverviewService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortfolioAllocationServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String ACCOUNT = "01K8D43J4YFN7X9R2B6C8M0V3Q";
  private static final String FIRST = "01K8D43J4YFN7X9R2B6C8M0V3R";
  private static final String SECOND = "01K8D43J4YFN7X9R2B6C8M0V3S";
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-31T01:00:00Z"), ZoneOffset.UTC);

  @Test
  void returnsNativeCurrencyAllocationCoordinatesWithoutClientMoneyArithmetic() {
    PortfolioOverviewService overview = new PortfolioOverviewService(new AllocationPortfolio(), instruments(), CLOCK);
    PortfolioAllocationService service = new PortfolioAllocationService(overview);

    var allocation = service.allocation(OWNER, LocalDate.of(2026, 7, 31));

    assertThat(allocation).singleElement().satisfies(currency -> {
      assertThat(currency.currency()).isEqualTo(CurrencyCode.CNY);
      assertThat(currency.marketValueCent()).isEqualTo(400L);
      assertThat(currency.slices()).extracting(PortfolioAllocation.Slice::instrumentId).containsExactly(SECOND, FIRST);
      assertThat(currency.slices()).extracting(PortfolioAllocation.Slice::shareBasisPoints).containsExactly(7_500, 2_500);
    });
  }

  private static InstrumentPort instruments() {
    Instrument first = Instrument.newActive(FIRST, "CN", "SSE", "ONE", "一号标的", AssetType.ETF,
        CurrencyCode.CNY, null, null, null);
    Instrument second = Instrument.newActive(SECOND, "CN", "SSE", "TWO", "二号标的", AssetType.ETF,
        CurrencyCode.CNY, null, null, null);
    return new InstrumentPort() {
      @Override public Optional<Instrument> findByNaturalKey(String market, String exchange, String symbol) {
        return Optional.empty();
      }
      @Override public Optional<Instrument> findById(String instrumentId) {
        return FIRST.equals(instrumentId) ? Optional.of(first) : SECOND.equals(instrumentId) ? Optional.of(second)
            : Optional.empty();
      }
      @Override public void insert(Instrument instrument, String futureContractId, String optionContractId) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static final class AllocationPortfolio implements PortfolioOverviewPort {
    @Override public List<PortfolioAccountBalance> findAccountBalances(String ownerUserId, LocalDate asOf) {
      return List.of(new PortfolioAccountBalance(ACCOUNT, CurrencyCode.CNY, 0L, 0L));
    }
    @Override public List<PortfolioOpenPosition> findOpenPositions(String ownerUserId, LocalDate asOf) {
      return List.of(new PortfolioOpenPosition(ACCOUNT, FIRST, CurrencyCode.CNY, BigDecimal.ONE),
          new PortfolioOpenPosition(ACCOUNT, SECOND, CurrencyCode.CNY, BigDecimal.valueOf(3L)));
    }
    @Override public List<PortfolioManualValuation> findManualValuations(String ownerUserId, LocalDate asOf) {
      LocalDateTime created = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
      return List.of(new PortfolioManualValuation(FIRST, CurrencyCode.CNY, asOf, 100L, null, (short) 1, null, created),
          new PortfolioManualValuation(SECOND, CurrencyCode.CNY, asOf, 100L, null, (short) 1, null, created));
    }
    @Override public long currentLedgerVersion(String ownerUserId) { return 3L; }
  }
}
