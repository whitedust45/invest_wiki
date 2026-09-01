package com.personal.investment.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.market.domain.InstrumentStatus;
import com.personal.investment.market.domain.FutureSpecification;
import com.personal.investment.portfolio.domain.ManualValuation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManualValuationServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String INSTRUMENT = "01K8D43J4YFN7X9R2B6C8M0V3I";

  @Test
  void appendsManualValuationAtFixedPriorityAndRetainsOriginalCurrencyMinorUnit() {
    CapturingPort port = new CapturingPort();
    ManualValuationService service = new ManualValuationService(instrument(CurrencyCode.USD), port,
        () -> "01K8D43J4YFN7X9R2B6C8M0V3V");

    ManualValuation valuation = service.record(OWNER, new ManualValuationCommand(INSTRUMENT,
        LocalDate.of(2026, 7, 27), CurrencyCode.USD, 666L, null, Instant.parse("2026-08-01T00:00:00Z"), "手工估值"));

    assertThat(valuation.manualValuationId()).isEqualTo("01K8D43J4YFN7X9R2B6C8M0V3V");
    assertThat(valuation.ownerUserId()).isEqualTo(OWNER);
    assertThat(valuation.unitPriceCent()).isEqualTo(666L);
    assertThat(valuation.marketValueCent()).isNull();
    assertThat(valuation.priority()).isEqualTo((short) 100);
    assertThat(port.appended).isEqualTo(valuation);
  }

  @Test
  void rejectsAmbiguousNonPositiveAndCrossCurrencyValuations() {
    ManualValuationService service = new ManualValuationService(instrument(CurrencyCode.USD), value -> { },
        () -> "01K8D43J4YFN7X9R2B6C8M0V3V");

    assertThatIllegalArgumentException().isThrownBy(() -> service.record(OWNER, command(null, null, CurrencyCode.USD)))
        .withMessageContaining("exactly one");
    assertThatIllegalArgumentException().isThrownBy(() -> service.record(OWNER, command(1L, 2L, CurrencyCode.USD)))
        .withMessageContaining("exactly one");
    assertThatIllegalArgumentException().isThrownBy(() -> service.record(OWNER, command(0L, null, CurrencyCode.USD)))
        .withMessageContaining("positive");
    assertThatIllegalArgumentException().isThrownBy(() -> service.record(OWNER, command(1L, null, CurrencyCode.CNY)))
        .withMessageContaining("native currency");
  }

  @Test
  void rejectsFuturesManualUnitPriceAndTotalMarketValue() {
    Instrument future = new Instrument(INSTRUMENT, "CFFEX", "CFFEX", "IC2608", "测试期货", AssetType.FUTURE,
        CurrencyCode.CNY, LocalDate.of(2026, 8, 21), InstrumentStatus.ACTIVE, new FutureSpecification("IC", 20_000L),
        null);
    ManualValuationService service = new ManualValuationService(portFor(future), value -> { },
        () -> "01K8D43J4YFN7X9R2B6C8M0V3V");

    assertThatIllegalArgumentException().isThrownBy(() -> service.record(OWNER,
        new ManualValuationCommand(INSTRUMENT, LocalDate.of(2026, 7, 27), CurrencyCode.CNY, 1L, null, null, null)))
        .withMessageContaining("daily settlement");
    assertThatIllegalArgumentException().isThrownBy(() -> service.record(OWNER,
        new ManualValuationCommand(INSTRUMENT, LocalDate.of(2026, 7, 27), CurrencyCode.CNY, null, 1L, null, null)))
        .withMessageContaining("daily settlement");
  }

  private static ManualValuationCommand command(Long unitPriceCent, Long marketValueCent, CurrencyCode currency) {
    return new ManualValuationCommand(INSTRUMENT, LocalDate.of(2026, 7, 27), currency, unitPriceCent,
        marketValueCent, null, null);
  }

  private static InstrumentPort instrument(CurrencyCode currency) {
    Instrument instrument = new Instrument(INSTRUMENT, "US", "NASDAQ", "TEST", "测试标的", AssetType.EQUITY,
        currency, null, InstrumentStatus.ACTIVE, null, null);
    return portFor(instrument);
  }

  private static InstrumentPort portFor(Instrument instrument) {
    return new InstrumentPort() {
      @Override
      public Optional<Instrument> findByNaturalKey(String market, String exchange, String symbol) {
        return Optional.empty();
      }

      @Override
      public Optional<Instrument> findById(String instrumentId) {
        return INSTRUMENT.equals(instrumentId) ? Optional.of(instrument) : Optional.empty();
      }

      @Override
      public void insert(Instrument ignored, String futureContractId, String optionContractId) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static final class CapturingPort implements ManualValuationPort {
    private ManualValuation appended;

    @Override
    public void append(ManualValuation valuation) {
      appended = valuation;
    }
  }
}
