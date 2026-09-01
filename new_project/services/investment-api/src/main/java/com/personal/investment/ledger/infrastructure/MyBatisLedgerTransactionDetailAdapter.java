package com.personal.investment.ledger.infrastructure;

import com.personal.investment.ledger.application.LedgerTransactionDetail;
import com.personal.investment.ledger.application.LedgerTransactionDetailPort;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerSourceType;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.PositionEffect;
import com.personal.investment.ledger.domain.PostingSide;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MyBatisLedgerTransactionDetailAdapter implements LedgerTransactionDetailPort {
  private final LedgerCorrectionMapper mapper;

  public MyBatisLedgerTransactionDetailAdapter(LedgerCorrectionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<LedgerTransactionDetail> find(String ownerUserId, String transactionId) {
    LedgerCorrectionMapper.TransactionRow transaction = mapper.findTransaction(ownerUserId, transactionId);
    if (transaction == null) {
      return Optional.empty();
    }
    boolean correctable = !"REVERSAL".equals(transaction.transactionType())
        && mapper.countDirectReversal(ownerUserId, transactionId) == 0;
    return Optional.of(new LedgerTransactionDetail(transaction.transactionId(),
        LedgerTransactionType.valueOf(transaction.transactionType()), transaction.occurredOn(), transaction.strategyKey(),
        transaction.operationGroupKey(), LedgerSourceType.valueOf(transaction.sourceType()), transaction.importExportFileId(),
        transaction.correctionRootTransactionId(), transaction.reversalOfTransactionId(), transaction.revisionNo(),
        transaction.ledgerVersion(), transaction.note(), correctable, mapper.findPostings(transactionId).stream()
            .map(row -> new LedgerTransactionDetail.Posting(row.postingId(), row.accountId(), row.postingNo(),
                PostingSide.valueOf(row.postingSide()), row.amountCent(), CurrencyCode.of(row.currency())))
            .toList(), mapper.findTradeDetails(transactionId).stream().map(row -> new LedgerTransactionDetail.TradeDetail(
                row.tradeDetailId(), row.detailNo(), row.instrumentId(), PositionEffect.valueOf(row.positionEffect()),
                row.quantity(), row.unitPriceCent(), row.pricePoints(), row.contractMultiplierCent(), row.deliveryDate(),
                row.feeCent(), row.optionContractMultiplier())).toList(), corporateAction(mapper.findCorporateAction(transactionId)),
        income(mapper.findIncome(transactionId))));
  }

  private static LedgerTransactionDetail.CorporateAction corporateAction(LedgerCorrectionMapper.CorporateActionRow row) {
    return row == null ? null : new LedgerTransactionDetail.CorporateAction(row.corporateActionId(), row.instrumentId(),
        row.actionType(), row.effectiveOn(), row.ratioNumerator(), row.ratioDenominator());
  }

  private static LedgerTransactionDetail.Income income(LedgerCorrectionMapper.IncomeRow row) {
    return row == null ? null : new LedgerTransactionDetail.Income(row.incomeDetailId(), row.incomeType(),
        row.instrumentId(), row.entitlementDate(), row.grossAmountCent(), row.taxWithheldCent(),
        row.perShareAmountCent(), CurrencyCode.of(row.currency()));
  }
}
