package com.personal.investment.ledger.application;

import java.util.Optional;

public interface FuturesInstrumentPort {
  Optional<FuturesInstrument> findById(String instrumentId);
}
