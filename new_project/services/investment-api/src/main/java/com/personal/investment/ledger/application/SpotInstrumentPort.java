package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.TradableInstrument;
import java.util.Optional;

/** Ledger read boundary; Market Infrastructure may implement it, but Ledger never writes Market data. */
public interface SpotInstrumentPort {
  Optional<TradableInstrument> findById(String instrumentId);
}
