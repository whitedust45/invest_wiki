package com.personal.investment.ledger.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FuturesDailySettlementCommand(String cashAccountId, String instrumentId, LocalDate occurredOn,
                                            BigDecimal settlementPricePoints, String note) {
}
