package com.personal.investment.ledger.infrastructure;

import com.personal.investment.ledger.application.HistoricalSpotTrade;
import com.personal.investment.ledger.application.HistoricalCorporateAction;
import com.personal.investment.ledger.application.SpotHistoryPort;
import com.personal.investment.ledger.application.CorporateActionType;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MyBatisSpotHistoryAdapter implements SpotHistoryPort {
  private final SpotHistoryMapper mapper;

  public MyBatisSpotHistoryAdapter(SpotHistoryMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<HistoricalSpotTrade> findAllByOwner(String ownerUserId) {
    return mapper.findAllByOwner(ownerUserId).stream().map(row -> new HistoricalSpotTrade(row.transactionId(),
        LedgerTransactionType.valueOf(row.transactionType()), row.occurredOn(), row.cashAccountId(),
        row.instrumentId(), row.tradeDetailId(), row.detailNo(), row.quantity(), row.unitPriceCent(), row.feeCent(),
        row.optionContractMultiplier(), CurrencyCode.of(row.currency()))).toList();
  }

  @Override
  public List<HistoricalCorporateAction> findCorporateActionsByOwner(String ownerUserId) {
    return mapper.findCorporateActionsByOwner(ownerUserId).stream().map(row -> new HistoricalCorporateAction(
        row.transactionId(), row.effectiveOn(), row.instrumentId(), CorporateActionType.valueOf(row.actionType()),
        row.ratioNumerator(), row.ratioDenominator())).toList();
  }
}
