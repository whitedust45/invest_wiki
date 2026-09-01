package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerTransaction;

public record OptionTradeResult(LedgerTransaction transaction, long grossPremiumCent, long allocatedCostCent,
                                long netRealizedPnlCent) {
}
