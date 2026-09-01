package com.personal.investment.portfolio.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.time.Instant;
import java.time.LocalDate;

public record ManualValuationCommand(String instrumentId, LocalDate valuationDate, CurrencyCode currency,
                                     Long unitPriceCent, Long marketValueCent, Instant validUntil, String note) {
}
