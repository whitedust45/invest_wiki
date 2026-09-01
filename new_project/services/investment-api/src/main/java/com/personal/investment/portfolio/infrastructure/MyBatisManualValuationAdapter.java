package com.personal.investment.portfolio.infrastructure;

import com.personal.investment.portfolio.application.ManualValuationPort;
import com.personal.investment.portfolio.domain.ManualValuation;
import org.springframework.stereotype.Component;

@Component
public class MyBatisManualValuationAdapter implements ManualValuationPort {
  private final ManualValuationMapper mapper;

  public MyBatisManualValuationAdapter(ManualValuationMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void append(ManualValuation valuation) {
    if (mapper.insert(new ManualValuationMapper.InsertRow(valuation.manualValuationId(), valuation.ownerUserId(),
        valuation.instrumentId(), valuation.valuationDate(), valuation.marketValueCent(), valuation.unitPriceCent(),
        valuation.currency().name(), valuation.priority(), valuation.validUntil(), valuation.note(),
        valuation.createdByUserId())) != 1) {
      throw new IllegalStateException("manual valuation was not appended");
    }
  }
}
