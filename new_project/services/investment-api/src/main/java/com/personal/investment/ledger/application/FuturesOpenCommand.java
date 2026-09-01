package com.personal.investment.ledger.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FuturesOpenCommand(String cashAccountId, String instrumentId, LocalDate occurredOn,
                                 BigDecimal quantity, BigDecimal pricePoints, long initialMarginCent,
                                 long feeCent, String note) {
}
