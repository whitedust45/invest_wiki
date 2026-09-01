package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.LedgerTradeDetail;
import com.personal.investment.ledger.domain.PositionEffect;
import com.personal.investment.ledger.domain.PostingSide;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Appends an immutable mirror reversal; it never updates or deletes the target facts. */
@Service
public class LedgerCorrectionService {
  private final LedgerCorrectionPort correctionPort;
  private final LedgerTransactionPort transactionPort;
  private final LedgerIdGenerator idGenerator;
  private final LedgerTransactionEventPort transactionEventPort;
  private final CorporateActionPort corporateActionPort;
  private final SpotHistoryReplayer historyReplayer;
  private final FuturesHistoryReplayer futuresHistoryReplayer;

  public LedgerCorrectionService(LedgerCorrectionPort correctionPort, LedgerTransactionPort transactionPort,
      LedgerIdGenerator idGenerator, LedgerTransactionEventPort transactionEventPort,
      CorporateActionPort corporateActionPort, SpotHistoryReplayer historyReplayer) {
    this(correctionPort, transactionPort, idGenerator, transactionEventPort, corporateActionPort, historyReplayer,
        FuturesHistoryReplayer.noop());
  }

  @Autowired
  public LedgerCorrectionService(LedgerCorrectionPort correctionPort, LedgerTransactionPort transactionPort,
      LedgerIdGenerator idGenerator, LedgerTransactionEventPort transactionEventPort,
      CorporateActionPort corporateActionPort, SpotHistoryReplayer historyReplayer,
      FuturesHistoryReplayer futuresHistoryReplayer) {
    this.correctionPort = correctionPort;
    this.transactionPort = transactionPort;
    this.idGenerator = idGenerator;
    this.transactionEventPort = transactionEventPort;
    this.corporateActionPort = corporateActionPort;
    this.historyReplayer = historyReplayer;
    this.futuresHistoryReplayer = futuresHistoryReplayer;
  }

  @Transactional
  public CorrectionResult reverse(String ownerUserId, String targetTransactionId) {
    return correct(ownerUserId, targetTransactionId, null);
  }

  /** Reversal and replacement share one owner lock, one local transaction and consecutive ledger versions. */
  @Transactional
  public CorrectionResult correct(String ownerUserId, String targetTransactionId,
      CorrectionReplacementAppender replacementAppender) {
    require(ownerUserId, "ownerUserId");
    require(targetTransactionId, "targetTransactionId");
    long lockedVersion = transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    CorrectionTarget target = correctionPort.findTarget(ownerUserId, targetTransactionId)
        .orElseThrow(() -> new CorrectionRejectedException("target transaction was not found"));
    LedgerTransaction original = target.transaction();
    if (original.transactionType() == LedgerTransactionType.REVERSAL) {
      throw new CorrectionRejectedException("a reversal transaction cannot be reversed directly");
    }
    if (correctionPort.hasDirectReversal(ownerUserId, targetTransactionId)) {
      throw new CorrectionRejectedException("target transaction already has a direct reversal");
    }
    int revisionNo = correctionPort.nextRevisionNo(ownerUserId, original.correctionRootTransactionId());
    if (revisionNo < 1) {
      throw new IllegalStateException("correction revision sequence is invalid");
    }
    long ledgerVersion = transactionPort.reserveNextLedgerVersion(ownerUserId, lockedVersion);
    String reversalTransactionId = idGenerator.next();
    LedgerTransaction reversal = LedgerTransaction.reversal(reversalTransactionId, ownerUserId, original.occurredOn(),
        original.correctionRootTransactionId(), original.transactionId(), revisionNo, ledgerVersion,
        "Correction reversal of " + original.transactionId(), mirrorPostings(original), mirrorTradeDetails(original));
    LedgerAppendMetadata.withStrategyKey(target.strategyKey(), () -> {
      transactionPort.append(reversal);
      return null;
    });
    if (target.corporateAction() != null) {
      CorporateActionDetail action = target.corporateAction();
      corporateActionPort.insert(new CorporateActionDetail(idGenerator.next(), reversalTransactionId,
          action.instrumentId(), action.actionType(), action.effectiveOn(), action.ratioDenominator(),
          action.ratioNumerator()));
    }
    transactionEventPort.recordAppended(reversal);
    if (replacementAppender == null) {
      rebuildProjections(ownerUserId, ledgerVersion);
      return new CorrectionResult(reversal, null);
    }
    // A replacement sell must validate against the state after its target has been removed.
    rebuildProjections(ownerUserId, ledgerVersion);
    int replacementRevisionNo;
    try {
      replacementRevisionNo = Math.addExact(revisionNo, 1);
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("correction revision number overflow", exception);
    }
    long replacementLedgerVersion = transactionPort.reserveNextLedgerVersion(ownerUserId, ledgerVersion);
    LedgerTransaction replacement = LedgerAppendMetadata.withStrategyKey(target.strategyKey(), () ->
        replacementAppender.append(new LedgerAppendContext(ownerUserId, original.correctionRootTransactionId(),
            replacementRevisionNo, replacementLedgerVersion, target.strategyKey())));
    if (replacement.ledgerVersion() != replacementLedgerVersion
        || replacement.revisionNo() != replacementRevisionNo
        || !replacement.correctionRootTransactionId().equals(original.correctionRootTransactionId())) {
      throw new IllegalStateException("replacement did not preserve correction context");
    }
    rebuildProjections(ownerUserId, replacementLedgerVersion);
    return new CorrectionResult(reversal, replacement);
  }

  private void rebuildProjections(String ownerUserId, long sourceLedgerVersion) {
    historyReplayer.rebuild(ownerUserId, sourceLedgerVersion);
    futuresHistoryReplayer.rebuild(ownerUserId, sourceLedgerVersion);
  }

  private List<LedgerPostingFact> mirrorPostings(LedgerTransaction original) {
    return original.postings().stream().map(posting -> new LedgerPostingFact(idGenerator.next(), posting.accountId(),
        posting.postingNo(), posting.side() == PostingSide.DEBIT ? PostingSide.CREDIT : PostingSide.DEBIT,
        posting.amount())).toList();
  }

  private List<LedgerTradeDetail> mirrorTradeDetails(LedgerTransaction original) {
    return original.tradeDetails().stream().map(detail -> new LedgerTradeDetail(idGenerator.next(), detail.detailNo(),
        detail.instrumentId(), inverse(detail.positionEffect()), detail.quantity(), detail.unitPriceCent(),
        detail.pricePoints(), detail.contractMultiplierCent(), detail.deliveryDate(), detail.feeCent(),
        detail.optionContractMultiplier())).toList();
  }

  private static PositionEffect inverse(PositionEffect effect) {
    return switch (effect) {
      case OPEN -> PositionEffect.CLOSE;
      case CLOSE -> PositionEffect.OPEN;
      case NONE -> PositionEffect.NONE;
    };
  }

  private static void require(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
