package com.personal.investment.ledger.application;

import java.util.Optional;

public interface LedgerCorrectionPort {
  Optional<CorrectionTarget> findTarget(String ownerUserId, String transactionId);

  boolean hasDirectReversal(String ownerUserId, String transactionId);

  int nextRevisionNo(String ownerUserId, String correctionRootTransactionId);
}
