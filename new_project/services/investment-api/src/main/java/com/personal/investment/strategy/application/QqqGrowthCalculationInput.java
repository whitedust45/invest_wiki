package com.personal.investment.strategy.application;

/** USD values are minor-unit integers; marketInputAvailable covers current QQQ and QLD unit-price snapshots. */
public record QqqGrowthCalculationInput(String inputVersion, boolean hasLedgerFacts, boolean hasCurrencyMismatch,
                                        boolean marketInputAvailable, long referenceNavCent, long qqqMarketValueCent,
                                        long qldMarketValueCent) implements StrategyCalculationInput {
}
