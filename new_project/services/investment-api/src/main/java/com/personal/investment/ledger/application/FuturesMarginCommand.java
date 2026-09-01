package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.MarginDirection;
import com.personal.investment.ledger.domain.Money;
import java.time.LocalDate;

public record FuturesMarginCommand(String cashAccountId, LocalDate occurredOn, MarginDirection direction,
                                   Money amount, String note) {
}
