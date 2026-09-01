package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerTransaction;

public record CorrectionResult(LedgerTransaction reversal, LedgerTransaction replacement) {
}
