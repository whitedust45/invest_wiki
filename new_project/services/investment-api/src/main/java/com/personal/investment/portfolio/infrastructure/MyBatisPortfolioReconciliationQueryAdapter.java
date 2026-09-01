package com.personal.investment.portfolio.infrastructure;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.portfolio.application.PortfolioReconciliationQueryPort;
import com.personal.investment.portfolio.application.PortfolioReconciliationView;
import com.personal.investment.portfolio.application.ReconciliationCursor;
import com.personal.investment.portfolio.domain.CashDifferenceDirection;
import com.personal.investment.portfolio.domain.ReconciliationPosition;
import com.personal.investment.portfolio.domain.ReconciliationStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MyBatisPortfolioReconciliationQueryAdapter implements PortfolioReconciliationQueryPort {
  private final PortfolioReconciliationQueryMapper mapper;

  public MyBatisPortfolioReconciliationQueryAdapter(PortfolioReconciliationQueryMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<PortfolioReconciliationView> find(String ownerUserId, ReconciliationCursor cursor, int limit,
      String cashAccountId, LocalDate from, LocalDate to) {
    return mapper.find(ownerUserId, cursor == null ? null : cursor.reconciliationDate(),
        cursor == null ? null : cursor.sourceLedgerVersion(), cursor == null ? null : cursor.createdAt(),
        cursor == null ? null : cursor.reconciliationId(), limit, cashAccountId, from, to).stream()
        .map(row -> new PortfolioReconciliationView(row.reconciliationId(), row.cashAccountId(),
            row.reconciliationDate(), Long.toString(row.brokerCashCent()), Long.toString(row.ledgerCashCent()),
            Long.toString(row.cashDifferenceCent()), CashDifferenceDirection.valueOf(row.cashDifferenceDirection()),
            CurrencyCode.of(row.currency()), ReconciliationStatus.valueOf(row.status()), row.sourceLedgerVersion(),
            row.createdAt(), mapper.findPositions(row.reconciliationId()).stream().map(position ->
                new ReconciliationPosition(position.reconciliationPositionId(), position.instrumentId(),
                    position.brokerQuantity(), position.ledgerQuantity(), position.quantityDifference())).toList()))
        .toList();
  }
}
