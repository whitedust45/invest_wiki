package com.personal.investment.strategy.application;

/** CNY pool/margin are minor-unit integers; PB percentiles and days-to-maturity are non-monetary inputs. */
public record IcImCalculationInput(String inputVersion, boolean hasLedgerFacts, boolean hasCurrencyMismatch,
                                   boolean hasRequiredInstrumentConfiguration, boolean marketInputAvailable,
                                   long poolCent, long availableMarginCent, long lockedMarginCent,
                                   String icPbPercentile, String imPbPercentile, String icAnnualizedBasis,
                                   String imAnnualizedBasis, Integer nearestMaturityDays)
    implements StrategyCalculationInput {
}
