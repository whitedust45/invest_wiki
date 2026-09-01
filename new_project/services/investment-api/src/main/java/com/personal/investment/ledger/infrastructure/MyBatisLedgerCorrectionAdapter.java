package com.personal.investment.ledger.infrastructure;

import com.personal.investment.ledger.application.CorporateActionDetail;
import com.personal.investment.ledger.application.CorporateActionType;
import com.personal.investment.ledger.application.CorrectionTarget;
import com.personal.investment.ledger.application.LedgerCorrectionPort;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerSourceType;
import com.personal.investment.ledger.domain.LedgerTradeDetail;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.PositionEffect;
import com.personal.investment.ledger.domain.PostingSide;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MyBatisLedgerCorrectionAdapter implements LedgerCorrectionPort {
  private final LedgerCorrectionMapper mapper;

  public MyBatisLedgerCorrectionAdapter(LedgerCorrectionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<CorrectionTarget> findTarget(String ownerUserId, String transactionId) {
    LedgerCorrectionMapper.TransactionRow row = mapper.findTransaction(ownerUserId, transactionId);
    if (row == null) {
      return Optional.empty();
    }
    LedgerTransaction transaction = new LedgerTransaction(row.transactionId(), row.ownerUserId(),
        LedgerTransactionType.valueOf(row.transactionType()), row.occurredOn(), LedgerSourceType.valueOf(row.sourceType()),
        row.importExportFileId(), row.correctionRootTransactionId(), row.reversalOfTransactionId(), row.revisionNo(), row.ledgerVersion(),
        row.note(), mapper.findPostings(transactionId).stream().map(posting -> new LedgerPostingFact(posting.postingId(),
            posting.accountId(), posting.postingNo(), PostingSide.valueOf(posting.postingSide()),
            Money.of(posting.amountCent(), CurrencyCode.of(posting.currency())))).toList(),
        mapper.findTradeDetails(transactionId).stream().map(detail -> new LedgerTradeDetail(detail.tradeDetailId(),
            detail.detailNo(), detail.instrumentId(), PositionEffect.valueOf(detail.positionEffect()), detail.quantity(),
            detail.unitPriceCent(), detail.pricePoints(), detail.contractMultiplierCent(), detail.deliveryDate(),
            detail.feeCent(), detail.optionContractMultiplier())).toList());
    LedgerCorrectionMapper.CorporateActionRow action = mapper.findCorporateAction(transactionId);
    CorporateActionDetail corporateAction = action == null ? null : new CorporateActionDetail(action.corporateActionId(),
        action.transactionId(), action.instrumentId(), CorporateActionType.valueOf(action.actionType()),
        action.effectiveOn(), action.ratioNumerator(), action.ratioDenominator());
    return Optional.of(new CorrectionTarget(transaction, corporateAction, row.strategyKey()));
  }

  @Override
  public boolean hasDirectReversal(String ownerUserId, String transactionId) {
    return mapper.countDirectReversal(ownerUserId, transactionId) > 0;
  }

  @Override
  public int nextRevisionNo(String ownerUserId, String correctionRootTransactionId) {
    return mapper.nextRevisionNo(ownerUserId, correctionRootTransactionId);
  }
}
