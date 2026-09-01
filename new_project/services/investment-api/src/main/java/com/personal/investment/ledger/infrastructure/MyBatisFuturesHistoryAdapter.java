package com.personal.investment.ledger.infrastructure;

import com.personal.investment.ledger.application.FuturesHistoryPort;
import com.personal.investment.ledger.application.HistoricalFuturesTrade;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MyBatisFuturesHistoryAdapter implements FuturesHistoryPort {
  private final FuturesHistoryMapper mapper;

  public MyBatisFuturesHistoryAdapter(FuturesHistoryMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<HistoricalFuturesTrade> findAllByOwner(String ownerUserId) {
    return mapper.findAllByOwner(ownerUserId).stream().map(row -> new HistoricalFuturesTrade(row.transactionId(),
        LedgerTransactionType.valueOf(row.transactionType()), row.occurredOn(), row.cashAccountId(),
        row.lockedMarginAccountId(), row.instrumentId(), row.tradeDetailId(), row.detailNo(), row.quantity(),
        row.pricePoints(), row.contractMultiplierCent(), row.initialMarginCent(), CurrencyCode.of(row.currency()))).toList();
  }
}
