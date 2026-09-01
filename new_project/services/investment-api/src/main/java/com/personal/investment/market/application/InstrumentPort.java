package com.personal.investment.market.application;

import com.personal.investment.market.domain.Instrument;
import java.util.List;
import java.util.Optional;

public interface InstrumentPort {
  Optional<Instrument> findByNaturalKey(String market, String exchange, String symbol);

  Optional<Instrument> findById(String instrumentId);

  default List<Instrument> findAll() {
    return List.of();
  }

  default void updateTushareCode(String instrumentId, String tushareCode) {
    throw new UnsupportedOperationException("instrument source-code update is not available");
  }

  void insert(Instrument instrument, String futureContractId, String optionContractId);
}
