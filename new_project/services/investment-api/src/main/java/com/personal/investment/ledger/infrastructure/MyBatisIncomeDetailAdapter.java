package com.personal.investment.ledger.infrastructure;

import com.personal.investment.ledger.application.IncomeDetail;
import com.personal.investment.ledger.application.IncomeDetailPort;
import org.springframework.stereotype.Component;

@Component
public class MyBatisIncomeDetailAdapter implements IncomeDetailPort {
  private final IncomeDetailMapper mapper;

  public MyBatisIncomeDetailAdapter(IncomeDetailMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void insert(IncomeDetail detail) {
    if (mapper.insert(new IncomeDetailMapper.IncomeDetailRow(detail.incomeDetailId(), detail.transactionId(),
        detail.incomeType(), detail.instrumentId(), detail.entitlementDate(), detail.grossAmountCent(),
        detail.taxWithheldCent(), detail.perShareAmountCent(), detail.currency().name())) != 1) {
      throw new IllegalStateException("income detail was not inserted");
    }
  }
}
