package com.personal.investment.strategy.application;

import java.time.LocalDate;
import java.math.BigDecimal;

/** USD premium and reference NAV use minor-unit integers; expiry is an immutable option-contract attribute. */
public record DeepPutCalculationInput(String inputVersion, boolean hasLedgerFacts, boolean hasCurrencyMismatch,
                                      boolean hasRequiredInstrumentConfiguration, boolean referenceNavAvailable,
                                      long referenceNavCent, long trailingPremiumCent,
                                      BigDecimal openPutQuantity, LocalDate asOfDate,
                                      LocalDate nearestExpiryDate) implements StrategyCalculationInput {
}
