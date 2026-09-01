package com.personal.investment.strategy.application;

/** Immutable, owner-scoped input assembled from ledger and already-confirmed local market snapshots. */
public sealed interface StrategyCalculationInput permits HighDividendCalculationInput, QqqGrowthCalculationInput,
    IcImCalculationInput, DeepPutCalculationInput {
  String inputVersion();

  boolean hasLedgerFacts();

  boolean hasCurrencyMismatch();

  /** A strategy may not turn missing contract metadata into a numeric market signal. */
  default boolean hasRequiredInstrumentConfiguration() {
    return true;
  }
}
