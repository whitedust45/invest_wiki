package com.personal.investment.ledger.infrastructure;

import com.personal.investment.ledger.application.LedgerTransactionQueryPort;
import com.personal.investment.ledger.application.LedgerTransactionSummary;
import com.personal.investment.ledger.application.TransactionCursor;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.LedgerSourceType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MyBatisLedgerTransactionQueryAdapter implements LedgerTransactionQueryPort {
  private final LedgerTransactionQueryMapper mapper;

  public MyBatisLedgerTransactionQueryAdapter(LedgerTransactionQueryMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<LedgerTransactionSummary> find(String ownerUserId, TransactionCursor cursor, int limit,
      String accountId, String instrumentId, LedgerTransactionType transactionType, String strategyKey,
      String search, LocalDate from, LocalDate to) {
    return mapper.find(ownerUserId, cursor == null ? null : cursor.occurredOn(),
        cursor == null ? null : cursor.transactionId(), limit, accountId, instrumentId,
        transactionType == null ? null : transactionType.name(), strategyKey, search, from, to).stream()
        .map(row -> new LedgerTransactionSummary(row.transactionId(), LedgerTransactionType.valueOf(row.transactionType()),
            row.occurredOn(), row.currency() == null ? null : CurrencyCode.of(row.currency()), row.ledgerVersion(),
            LedgerSourceType.valueOf(row.sourceType()), row.importExportFileId()))
        .toList();
  }
}
