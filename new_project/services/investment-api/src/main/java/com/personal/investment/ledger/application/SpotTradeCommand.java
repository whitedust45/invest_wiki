package com.personal.investment.ledger.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpotTradeCommand(String cashAccountId, String instrumentId, LocalDate occurredOn, BigDecimal quantity,
                               long unitPriceCent, long feeCent, String note) {
}
