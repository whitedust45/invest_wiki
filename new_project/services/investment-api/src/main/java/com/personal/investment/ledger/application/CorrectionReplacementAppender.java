package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerTransaction;

@FunctionalInterface
public interface CorrectionReplacementAppender {
  LedgerTransaction append(LedgerAppendContext context);
}
