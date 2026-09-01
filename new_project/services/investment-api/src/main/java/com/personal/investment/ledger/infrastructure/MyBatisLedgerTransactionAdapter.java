package com.personal.investment.ledger.infrastructure;

import com.personal.investment.ledger.application.LedgerTransactionPort;
import com.personal.investment.ledger.application.LedgerAppendMetadata;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.PostingSide;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MyBatisLedgerTransactionAdapter implements LedgerTransactionPort {
  private final LedgerTransactionMapper mapper;

  public MyBatisLedgerTransactionAdapter(LedgerTransactionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long lockCurrentLedgerVersion(String ownerUserId, String newLedgerStateId) {
    mapper.ensureLedgerState(newLedgerStateId, ownerUserId);
    return mapper.lockCurrentLedgerVersion(ownerUserId);
  }

  @Override
  public long reserveNextLedgerVersion(String ownerUserId, long lockedLedgerVersion) {
    long nextVersion;
    try {
      nextVersion = Math.addExact(lockedLedgerVersion, 1L);
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("ledger version overflow", exception);
    }
    if (mapper.updateLedgerVersion(ownerUserId, nextVersion) != 1) {
      throw new IllegalStateException("ledger state changed while locked");
    }
    return nextVersion;
  }

  @Override
  public List<LedgerPostingFact> findPostingFactsByOwner(String ownerUserId) {
    return mapper.findPostingFactsByOwner(ownerUserId).stream()
        .map(row -> new LedgerPostingFact(row.postingId(), row.accountId(), row.postingNo(),
            PostingSide.valueOf(row.postingSide()), Money.of(row.amountCent(), CurrencyCode.of(row.currency()))))
        .toList();
  }

  @Override
  public boolean hasAnyTransactionByOwner(String ownerUserId) {
    return mapper.hasAnyTransactionByOwner(ownerUserId);
  }

  @Override
  public void append(LedgerTransaction transaction) {
    mapper.insertTransaction(new LedgerTransactionMapper.TransactionRow(transaction.transactionId(),
        transaction.ownerUserId(), transaction.transactionType().name(), LedgerAppendMetadata.strategyKey(),
        LedgerAppendMetadata.operationGroupKey(), transaction.occurredOn(),
        transaction.sourceType().name(), transaction.importExportFileId(), transaction.correctionRootTransactionId(), transaction.reversalOfTransactionId(),
        transaction.revisionNo(), transaction.note(), transaction.ownerUserId(), transaction.ledgerVersion()));
    for (LedgerPostingFact posting : transaction.postings()) {
      mapper.insertPosting(new LedgerTransactionMapper.PostingRow(posting.postingId(),
          transaction.transactionId(), posting.accountId(), posting.postingNo(), posting.side().name(),
          posting.amount().cent(), posting.amount().currency().name()));
    }
    for (var detail : transaction.tradeDetails()) {
      mapper.insertTradeDetail(new LedgerTransactionMapper.TradeDetailRow(detail.tradeDetailId(),
          transaction.transactionId(), detail.detailNo(), detail.instrumentId(), detail.positionEffect().name(),
          detail.quantity(), detail.unitPriceCent(), detail.pricePoints(), detail.contractMultiplierCent(),
          detail.deliveryDate(), detail.feeCent(), detail.optionContractMultiplier()));
    }
  }
}
