package com.personal.investment.ledger.infrastructure;

import com.personal.investment.ledger.application.CorporateActionDetail;
import com.personal.investment.ledger.application.CorporateActionPort;
import org.springframework.stereotype.Component;

@Component
public class MyBatisCorporateActionAdapter implements CorporateActionPort {
  private final CorporateActionMapper mapper;

  public MyBatisCorporateActionAdapter(CorporateActionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void insert(CorporateActionDetail detail) {
    if (mapper.insert(new CorporateActionMapper.Row(detail.corporateActionId(), detail.transactionId(),
        detail.instrumentId(), detail.actionType().name(), detail.effectiveOn(), detail.ratioNumerator(),
        detail.ratioDenominator())) != 1) {
      throw new IllegalStateException("corporate action detail was not inserted");
    }
  }
}
