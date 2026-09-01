package com.personal.investment.ledger.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OptionExpiryCommand(String cashAccountId, String instrumentId, LocalDate occurredOn,
                                  BigDecimal quantity, OptionExpiryOutcome expiryOutcome, String note) {
}
