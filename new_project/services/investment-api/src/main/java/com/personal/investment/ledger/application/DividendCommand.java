package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.Money;
import java.time.LocalDate;

public record DividendCommand(String cashAccountId, String instrumentId, LocalDate occurredOn,
                              LocalDate entitlementDate, Money grossAmount, Money taxWithheld,
                              Long perShareAmountCent, String note) {
}
