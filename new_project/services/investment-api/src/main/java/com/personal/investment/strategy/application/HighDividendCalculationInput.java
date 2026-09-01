package com.personal.investment.strategy.application;

/** CNY values are minor-unit integers; trailingIncomeCent includes only the preceding twelve months. */
public record HighDividendCalculationInput(String inputVersion, boolean hasLedgerFacts, boolean hasCurrencyMismatch,
                                           long cashBufferCent, long trailingIncomeCent)
    implements StrategyCalculationInput {
}
