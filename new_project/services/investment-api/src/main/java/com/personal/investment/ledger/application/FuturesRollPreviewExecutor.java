package com.personal.investment.ledger.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Validates an opening leg against the exact post-close ledger state, then always rolls the dry run back.
 * This is deliberately a separate Spring bean so REQUIRES_NEW is applied through a proxy.
 */
@Component
public class FuturesRollPreviewExecutor {
  private final FuturesCloseService closeService;
  private final FuturesOpenService openService;

  public FuturesRollPreviewExecutor(FuturesCloseService closeService, FuturesOpenService openService) {
    this.closeService = closeService;
    this.openService = openService;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public FuturesOpenPreviewResult previewOpenAfterClose(String ownerUserId, FuturesRollCommand command) {
    try {
      closeService.close(ownerUserId, command.closeLeg());
      return openService.preview(ownerUserId, command.openLeg());
    } finally {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
  }
}
