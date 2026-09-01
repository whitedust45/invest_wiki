package com.personal.investment.ledger.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FuturesRollService {
  private final FuturesCloseService closeService;
  private final FuturesOpenService openService;
  private final FuturesRollPreviewExecutor previewExecutor;
  private final LedgerIdGenerator idGenerator;

  public FuturesRollService(FuturesCloseService closeService, FuturesOpenService openService,
      FuturesRollPreviewExecutor previewExecutor, LedgerIdGenerator idGenerator) {
    this.closeService = closeService;
    this.openService = openService;
    this.previewExecutor = previewExecutor;
    this.idGenerator = idGenerator;
  }

  /** Both persisted legs join this outer transaction; a failure of either leg rolls the entire group back. */
  @Transactional
  public FuturesRollResult roll(String ownerUserId, FuturesRollCommand command) {
    validate(command);
    String operationGroupKey = idGenerator.next();
    return LedgerAppendMetadata.withOperationGroupKey(operationGroupKey, () -> {
      FuturesCloseResult close = closeService.close(ownerUserId, command.closeLeg());
      FuturesOpenResult open = openService.open(ownerUserId, command.openLeg());
      return new FuturesRollResult(operationGroupKey, close, open);
    });
  }

  /**
   * Preview validates the opening leg after the close has released its exact margin, then rolls the dry run back.
   * Merely checking the two legs independently would reject a valid roll financed by released margin.
   */
  public FuturesRollPreviewResult preview(String ownerUserId, FuturesRollCommand command) {
    validate(command);
    FuturesClosePreviewResult closePreview = closeService.preview(ownerUserId, command.closeLeg());
    FuturesOpenPreviewResult openPreview = previewExecutor.previewOpenAfterClose(ownerUserId, command);
    return new FuturesRollPreviewResult(idGenerator.next(), closePreview, openPreview);
  }

  private static void validate(FuturesRollCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("futures roll command is required");
    }
  }
}
