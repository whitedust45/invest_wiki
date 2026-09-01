package com.personal.investment.ledger.application;

import java.util.Optional;

/** Ledger read boundary for complete active long-option contract metadata. */
public interface OptionInstrumentPort {
  Optional<OptionInstrument> findById(String instrumentId);
}
