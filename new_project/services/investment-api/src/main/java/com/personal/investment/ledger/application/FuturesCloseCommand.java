package com.personal.investment.ledger.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FuturesCloseCommand(String cashAccountId, String instrumentId, LocalDate occurredOn,
                                  BigDecimal quantity, BigDecimal pricePoints, long feeCent, String note) {
}
