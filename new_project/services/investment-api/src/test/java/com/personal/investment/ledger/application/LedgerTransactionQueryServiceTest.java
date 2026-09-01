package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerTransactionQueryServiceTest {
  @Test
  void returnsAnOpaqueCursorForTheLastIncludedOwnerScopedTransaction() {
    LedgerTransactionSummary first = new LedgerTransactionSummary("01K8D43J4YFN7X9R2B6C8M0V31",
        LedgerTransactionType.TRADE_BUY, LocalDate.of(2026, 7, 26), CurrencyCode.USD, 3);
    LedgerTransactionSummary second = new LedgerTransactionSummary("01K8D43J4YFN7X9R2B6C8M0V30",
        LedgerTransactionType.EXTERNAL_FUNDING, LocalDate.of(2026, 7, 25), CurrencyCode.USD, 2);
    CapturingPort port = new CapturingPort(List.of(first, second));
    LedgerTransactionQueryService service = new LedgerTransactionQueryService(port);

    LedgerTransactionPage firstPage = service.list("01K8D43J4YFN7X9R2B6C8M0V3P", null, 1,
        null, null, null, null, null, null, null);
    LedgerTransactionPage secondPage = service.list("01K8D43J4YFN7X9R2B6C8M0V3P", firstPage.nextCursor(), 1,
        null, null, null, null, null, null, null);

    assertThat(firstPage.items()).containsExactly(first);
    assertThat(firstPage.nextCursor()).isNotBlank();
    assertThat(secondPage.items()).containsExactly(first);
    assertThat(port.cursors).hasSize(2);
    assertThat(port.cursors.getFirst()).isNull();
    assertThat(port.cursors.getLast().occurredOn()).isEqualTo(first.occurredOn());
    assertThat(port.cursors.getLast().transactionId()).isEqualTo(first.transactionId());
  }

  private static final class CapturingPort implements LedgerTransactionQueryPort {
    private final List<LedgerTransactionSummary> rows;
    private final List<TransactionCursor> cursors = new java.util.ArrayList<>();

    private CapturingPort(List<LedgerTransactionSummary> rows) {
      this.rows = rows;
    }

    @Override
    public List<LedgerTransactionSummary> find(String ownerUserId, TransactionCursor cursor, int limit,
        String accountId, String instrumentId, LedgerTransactionType transactionType, String strategyKey,
        String search, LocalDate from, LocalDate to) {
      cursors.add(cursor);
      return rows;
    }
  }
}
