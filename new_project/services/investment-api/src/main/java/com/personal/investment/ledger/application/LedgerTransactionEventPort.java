package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerTransaction;

/** Transactional boundary for audit/outbox side effects of an already-appended ledger fact. */
@FunctionalInterface
public interface LedgerTransactionEventPort {
  void recordAppended(LedgerTransaction transaction);

  static LedgerTransactionEventPort noop() {
    return transaction -> { };
  }
}
