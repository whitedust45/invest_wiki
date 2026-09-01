package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.Money;
import java.time.LocalDate;

public record ExternalFundingCommand(String cashAccountId, LocalDate occurredOn, Money amount, String note) {
}
