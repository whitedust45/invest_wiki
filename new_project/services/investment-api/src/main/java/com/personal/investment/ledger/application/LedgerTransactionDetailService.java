package com.personal.investment.ledger.application;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerTransactionDetailService {
  private final LedgerTransactionDetailPort detailPort;

  public LedgerTransactionDetailService(LedgerTransactionDetailPort detailPort) {
    this.detailPort = detailPort;
  }

  @Transactional(readOnly = true)
  public Optional<LedgerTransactionDetail> find(String ownerUserId, String transactionId) {
    if (ownerUserId == null || !ownerUserId.matches("^[0-9A-HJKMNP-TV-Z]{26}$")
        || transactionId == null || !transactionId.matches("^[0-9A-HJKMNP-TV-Z]{26}$")) {
      throw new IllegalArgumentException("ledger detail identifier is invalid");
    }
    return detailPort.find(ownerUserId, transactionId);
  }
}
