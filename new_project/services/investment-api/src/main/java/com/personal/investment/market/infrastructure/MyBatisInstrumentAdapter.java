package com.personal.investment.market.infrastructure;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.FutureSpecification;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.market.domain.InstrumentStatus;
import com.personal.investment.market.domain.OptionRight;
import com.personal.investment.market.domain.OptionSpecification;
import java.util.Optional;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MyBatisInstrumentAdapter implements InstrumentPort {
  private final InstrumentMapper mapper;

  public MyBatisInstrumentAdapter(InstrumentMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Instrument> findByNaturalKey(String market, String exchange, String symbol) {
    return Optional.ofNullable(mapper.findByNaturalKey(market, exchange, symbol)).map(this::toDomain);
  }

  @Override
  public Optional<Instrument> findById(String instrumentId) {
    return Optional.ofNullable(mapper.findById(instrumentId)).map(this::toDomain);
  }

  @Override
  public List<Instrument> findAll() {
    return mapper.findAll().stream().map(this::toDomain).toList();
  }

  @Override
  public void updateTushareCode(String instrumentId, String tushareCode) {
    if (mapper.updateTushareCode(instrumentId, tushareCode) != 1) {
      throw new IllegalArgumentException("instrument was not found");
    }
  }

  @Override
  public void insert(Instrument instrument, String futureContractId, String optionContractId) {
    if (mapper.insertInstrument(toRow(instrument)) != 1) {
      throw new IllegalStateException("instrument was not inserted");
    }
    if (instrument.assetType() == AssetType.FUTURE) {
      if (futureContractId == null || optionContractId != null) {
        throw new IllegalArgumentException("future contract id is required only for futures");
      }
      FutureSpecification future = instrument.futureSpecification();
      mapper.insertFuture(new InstrumentMapper.FutureInsertRow(futureContractId, instrument.instrumentId(),
          future.productCode(), future.contractMultiplierCent(), instrument.nativeCurrency().name()));
    }
    if (instrument.assetType() == AssetType.OPTION) {
      if (optionContractId == null || futureContractId != null) {
        throw new IllegalArgumentException("option contract id is required only for options");
      }
      OptionSpecification option = instrument.optionSpecification();
      mapper.insertOption(new InstrumentMapper.OptionInsertRow(optionContractId, instrument.instrumentId(),
          option.underlyingInstrumentId(), option.optionRight().name(), option.strikePriceCent(),
          option.contractMultiplier(), instrument.nativeCurrency().name()));
    }
  }

  private Instrument toDomain(InstrumentMapper.InstrumentRow row) {
    AssetType assetType = AssetType.valueOf(row.assetType());
    FutureSpecification future = assetType == AssetType.FUTURE ? future(row.instrumentId()) : null;
    OptionSpecification option = assetType == AssetType.OPTION ? option(row.instrumentId()) : null;
    return new Instrument(row.instrumentId(), row.market(), row.exchange(), row.symbol(), row.displayName(),
        assetType, CurrencyCode.of(row.nativeCurrency()), row.maturityDate(), InstrumentStatus.valueOf(row.status()),
        future, option, row.tushareCode(), row.underlyingInstrumentId());
  }

  private FutureSpecification future(String instrumentId) {
    InstrumentMapper.FutureRow row = mapper.findFutureByInstrumentId(instrumentId);
    if (row == null) {
      throw new IllegalStateException("future instrument is missing its contract definition");
    }
    return new FutureSpecification(row.productCode(), row.contractMultiplierCent());
  }

  private OptionSpecification option(String instrumentId) {
    InstrumentMapper.OptionRow row = mapper.findOptionByInstrumentId(instrumentId);
    if (row == null) {
      throw new IllegalStateException("option instrument is missing its contract definition");
    }
    return new OptionSpecification(row.underlyingInstrumentId(), OptionRight.valueOf(row.optionRight()),
        row.strikePriceCent(), row.contractMultiplier());
  }

  private InstrumentMapper.InstrumentRow toRow(Instrument instrument) {
    return new InstrumentMapper.InstrumentRow(instrument.instrumentId(), instrument.market(), instrument.exchange(),
        instrument.symbol(), instrument.displayName(), instrument.assetType().name(),
        instrument.nativeCurrency().name(), instrument.maturityDate(), instrument.status().name(),
        instrument.tushareCode(), instrument.underlyingInstrumentId());
  }
}
