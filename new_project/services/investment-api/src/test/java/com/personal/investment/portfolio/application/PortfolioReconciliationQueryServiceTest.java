package com.personal.investment.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.portfolio.domain.CashDifferenceDirection;
import com.personal.investment.portfolio.domain.ReconciliationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioReconciliationQueryServiceTest {
  @Test
  void usesAStableBusinessDateLedgerVersionAndIdentifierCursor() {
    PortfolioReconciliationView newer = view("01K8D43J4YFN7X9R2B6C8M0V32", LocalDate.of(2026, 8, 21), 8);
    PortfolioReconciliationView older = view("01K8D43J4YFN7X9R2B6C8M0V31", LocalDate.of(2026, 8, 21), 7);
    PortfolioReconciliationQueryPort port = (owner, cursor, limit, cash, from, to) -> {
      if (cursor == null) {
        return List.of(newer, older);
      }
      return List.of(older);
    };
    PortfolioReconciliationQueryService service = new PortfolioReconciliationQueryService(port);

    PortfolioReconciliationPage first = service.list("01K8D43J4YFN7X9R2B6C8M0V3P", null, 1, null, null, null);
    PortfolioReconciliationPage second = service.list("01K8D43J4YFN7X9R2B6C8M0V3P", first.nextCursor(), 1, null,
        null, null);

    assertThat(first.items()).containsExactly(newer);
    assertThat(first.nextCursor()).isNotBlank();
    assertThat(second.items()).containsExactly(older);
    assertThat(second.nextCursor()).isNull();
  }

  private static PortfolioReconciliationView view(String id, LocalDate date, long ledgerVersion) {
    return new PortfolioReconciliationView(id, "01K8D43J4YFN7X9R2B6C8M0V3C", date, "100", "100", "0",
        CashDifferenceDirection.NONE, CurrencyCode.USD, ReconciliationStatus.MATCHED, ledgerVersion,
        LocalDateTime.of(2026, 8, 21, 12, 0), List.of());
  }
}
