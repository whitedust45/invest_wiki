package com.personal.investment.reporting.infrastructure;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.reporting.application.PortfolioHistoryPoint;
import com.personal.investment.reporting.application.PortfolioHistorySnapshotPort;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MyBatisPortfolioHistorySnapshotAdapter implements PortfolioHistorySnapshotPort {
  private final PortfolioHistorySnapshotMapper mapper;

  public MyBatisPortfolioHistorySnapshotAdapter(PortfolioHistorySnapshotMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public boolean exists(String ownerUserId, CurrencyCode currency, LocalDate asOfDate, long sourceLedgerVersion) {
    return mapper.exists(ownerUserId, currency.name(), asOfDate, sourceLedgerVersion);
  }

  @Override
  public void append(String ownerUserId, PortfolioHistoryPoint point) {
    mapper.insert(ownerUserId, point);
  }

  @Override
  public List<PortfolioHistoryPoint> list(String ownerUserId, CurrencyCode currency, LocalDate fromInclusive,
      LocalDate toInclusive, int limit) {
    return mapper.list(ownerUserId, currency.name(), fromInclusive, toInclusive, limit);
  }

  @Override
  public List<String> ownersWithLedger() {
    return mapper.ownersWithLedger();
  }
}
