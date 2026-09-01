package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.Money;
import java.time.LocalDate;

public record IncomeCommand(
    String cashAccountId, LocalDate occurredOn, Money grossAmount, Money taxWithheld, String note) {
}
