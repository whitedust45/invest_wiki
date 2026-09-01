package com.personal.investment.ledger.application;

import java.time.LocalDate;
import java.util.List;
import com.personal.investment.ledger.domain.LedgerTransactionType;

public interface LedgerTransactionQueryPort {
  List<LedgerTransactionSummary> find(String ownerUserId, TransactionCursor cursor, int limit,
                                      String accountId, String instrumentId, LedgerTransactionType transactionType,
                                      String strategyKey, String search, LocalDate from, LocalDate to);
}
