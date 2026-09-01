package com.personal.investment.ledger.application;

import java.time.LocalDate;

public record CorporateActionCommand(String instrumentId, LocalDate effectiveOn, CorporateActionType actionType,
                                    long ratioNumerator, long ratioDenominator, String note) {
}
