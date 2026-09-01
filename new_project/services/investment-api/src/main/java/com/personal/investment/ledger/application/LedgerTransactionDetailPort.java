package com.personal.investment.ledger.application;

import java.util.Optional;

public interface LedgerTransactionDetailPort {
  Optional<LedgerTransactionDetail> find(String ownerUserId, String transactionId);
}
