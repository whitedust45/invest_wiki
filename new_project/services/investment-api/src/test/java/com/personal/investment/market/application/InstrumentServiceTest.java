package com.personal.investment.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.FutureSpecification;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.market.domain.InstrumentConflictException;
import com.personal.investment.market.domain.OptionRight;
import com.personal.investment.market.domain.OptionSpecification;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InstrumentServiceTest {
  @Test
  void createsNormalizedEquityAndReturnsTheExistingFactForTheSameNaturalKeyAndDefinition() {
    InMemoryInstrumentPort port = new InMemoryInstrumentPort();
    InstrumentService service = new InstrumentService(port, new SequenceIdGenerator());
    CreateInstrumentCommand command = new CreateInstrumentCommand(" us ", " nasdaq ", " qqq ", "纳指 ETF",
        AssetType.ETF, CurrencyCode.USD, null, null, null);

    Instrument first = service.create(command);
    Instrument duplicate = service.create(command);

    assertThat(first.instrumentId()).isEqualTo(duplicate.instrumentId());
    assertThat(first.market()).isEqualTo("US");
    assertThat(first.exchange()).isEqualTo("NASDAQ");
    assertThat(first.symbol()).isEqualTo("QQQ");
    assertThat(port.instruments()).containsExactly(first);
  }

  @Test
  void rejectsAConflictingDefinitionForAnExistingNaturalKey() {
    InMemoryInstrumentPort port = new InMemoryInstrumentPort();
    InstrumentService service = new InstrumentService(port, new SequenceIdGenerator());
    service.create(new CreateInstrumentCommand("US", "NASDAQ", "QQQ", "纳指 ETF", AssetType.ETF,
        CurrencyCode.USD, null, null, null));

    assertThatThrownBy(() -> service.create(new CreateInstrumentCommand("US", "NASDAQ", "QQQ", "另一名称",
        AssetType.ETF, CurrencyCode.USD, null, null, null)))
        .isInstanceOf(InstrumentConflictException.class)
        .hasMessageContaining("INSTRUMENT_CONFLICT");
  }

  @Test
  void createsOnlyValidCffexFutureAndOptionDefinitions() {
    InMemoryInstrumentPort port = new InMemoryInstrumentPort();
    InstrumentService service = new InstrumentService(port, new SequenceIdGenerator());
    Instrument index = service.create(new CreateInstrumentCommand("CN", "CSI", "000905", "中证500", AssetType.INDEX,
        CurrencyCode.CNY, null, null, null, "000905.SH", null));
    Instrument underlying = service.create(new CreateInstrumentCommand("US", "NASDAQ", "QQQ", "纳指 ETF",
        AssetType.ETF, CurrencyCode.USD, null, null, null));

    Instrument option = service.create(new CreateInstrumentCommand("US", "OPRA", "QQQ260918C00600000",
        "QQQ Call", AssetType.OPTION, CurrencyCode.USD, LocalDate.of(2026, 9, 18), null,
        new OptionSpecification(underlying.instrumentId(), OptionRight.CALL, 60_000, 100)));
    Instrument future = service.create(new CreateInstrumentCommand("CFFEX", "CFFEX", "IC2608", "IC2608",
        AssetType.FUTURE, CurrencyCode.CNY, LocalDate.of(2026, 8, 21),
        new FutureSpecification("IC", 20_000), null, null, index.instrumentId()));

    assertThat(option.optionSpecification().underlyingInstrumentId()).isEqualTo(underlying.instrumentId());
    assertThat(future.futureSpecification().contractMultiplierCent()).isEqualTo(20_000);
    assertThatThrownBy(() -> service.create(new CreateInstrumentCommand("US", "CME", "IC2609", "错误期货",
        AssetType.FUTURE, CurrencyCode.USD, LocalDate.of(2026, 9, 18),
        new FutureSpecification("IC", 20_000), null, null, index.instrumentId())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CFFEX");
  }

  @Test
  void rejectsOptionWhoseUnderlyingIsNotACompatibleSameCurrencyEquityOrEtf() {
    InMemoryInstrumentPort port = new InMemoryInstrumentPort();
    InstrumentService service = new InstrumentService(port, new SequenceIdGenerator());
    Instrument index = service.create(new CreateInstrumentCommand("CN", "CSI", "000852", "中证1000", AssetType.INDEX,
        CurrencyCode.CNY, null, null, null, "000852.SH", null));
    Instrument future = service.create(new CreateInstrumentCommand("CFFEX", "CFFEX", "IM2608", "IM2608",
        AssetType.FUTURE, CurrencyCode.CNY, LocalDate.of(2026, 8, 21),
        new FutureSpecification("IM", 20_000), null, null, index.instrumentId()));

    assertThatThrownBy(() -> service.create(new CreateInstrumentCommand("US", "OPRA", "IM2608C", "错误期权",
        AssetType.OPTION, CurrencyCode.USD, LocalDate.of(2026, 9, 18), null,
        new OptionSpecification(future.instrumentId(), OptionRight.CALL, 1_000, 100))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("underlying");
  }

  @Test
  void rejectsFutureWithoutANonTradableIndexUnderlying() {
    InMemoryInstrumentPort port = new InMemoryInstrumentPort();
    InstrumentService service = new InstrumentService(port, new SequenceIdGenerator());
    Instrument etf = service.create(new CreateInstrumentCommand("CN", "SSE", "510500", "中证500ETF", AssetType.ETF,
        CurrencyCode.CNY, null, null, null));

    assertThatThrownBy(() -> service.create(new CreateInstrumentCommand("CFFEX", "CFFEX", "IC2608", "IC2608",
        AssetType.FUTURE, CurrencyCode.CNY, LocalDate.of(2026, 8, 21), new FutureSpecification("IC", 20_000),
        null, null, etf.instrumentId())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("index");
  }

  private static final class InMemoryInstrumentPort implements InstrumentPort {
    private final List<Instrument> instruments = new ArrayList<>();

    @Override
    public Optional<Instrument> findByNaturalKey(String market, String exchange, String symbol) {
      return instruments.stream().filter(instrument -> instrument.market().equals(market)
          && instrument.exchange().equals(exchange) && instrument.symbol().equals(symbol)).findFirst();
    }

    @Override
    public Optional<Instrument> findById(String instrumentId) {
      return instruments.stream().filter(instrument -> instrument.instrumentId().equals(instrumentId)).findFirst();
    }

    @Override
    public List<Instrument> findAll() {
      return List.copyOf(instruments);
    }

    @Override
    public void updateTushareCode(String instrumentId, String tushareCode) {
      throw new UnsupportedOperationException("not required by this test");
    }

    @Override
    public void insert(Instrument instrument, String futureContractId, String optionContractId) {
      instruments.add(instrument);
    }

    List<Instrument> instruments() {
      return List.copyOf(instruments);
    }
  }

  private static final class SequenceIdGenerator implements LedgerIdGenerator {
    private int sequence;

    @Override
    public String next() {
      return "01K8D43J4YFN7X9R2B6C8M0V" + String.format("%02d", sequence++);
    }
  }
}
