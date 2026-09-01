package com.personal.investment.market.domain;

public class InstrumentConflictException extends IllegalStateException {
  public InstrumentConflictException(String market, String exchange, String symbol) {
    super("INSTRUMENT_CONFLICT: " + market + "/" + exchange + "/" + symbol);
  }
}
