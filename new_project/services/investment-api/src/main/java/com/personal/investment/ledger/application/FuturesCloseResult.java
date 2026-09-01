package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerTransaction;

public record FuturesCloseResult(LedgerTransaction transaction, long releasedMarginCent, long realizedPnlCent) {
}
