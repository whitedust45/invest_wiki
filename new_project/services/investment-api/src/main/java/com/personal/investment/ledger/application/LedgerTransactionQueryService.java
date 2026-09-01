package com.personal.investment.ledger.application;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerTransactionQueryService {
  private static final int MAX_LIMIT = 100;
  private final LedgerTransactionQueryPort queryPort;

  public LedgerTransactionQueryService(LedgerTransactionQueryPort queryPort) {
    this.queryPort = queryPort;
  }

  @Transactional(readOnly = true)
  public LedgerTransactionPage list(String ownerUserId, String cursorValue, int limit, String accountId,
      String instrumentId, LedgerTransactionType transactionType, String strategyKey, String search,
      LocalDate from, LocalDate to) {
    if (ownerUserId == null || ownerUserId.isBlank()) {
      throw new IllegalArgumentException("ownerUserId must not be blank");
    }
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
    }
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("from must not be after to");
    }
    if (strategyKey != null && !strategyKey.matches("HIGH_DIVIDEND|QQQ_GROWTH|IC_IM|DEEP_PUT")) {
      throw new IllegalArgumentException("strategyKey is invalid");
    }
    String normalizedSearch = search == null ? null : search.trim();
    if (normalizedSearch != null && normalizedSearch.isEmpty()) {
      normalizedSearch = null;
    }
    if (normalizedSearch != null && normalizedSearch.length() > 128) {
      throw new IllegalArgumentException("search must not exceed 128 characters");
    }
    TransactionCursor cursor = cursorValue == null || cursorValue.isBlank() ? null : decode(cursorValue);
    List<LedgerTransactionSummary> rows = queryPort.find(ownerUserId, cursor, limit + 1, accountId, instrumentId,
        transactionType, strategyKey, normalizedSearch, from, to);
    boolean hasMore = rows.size() > limit;
    List<LedgerTransactionSummary> items = hasMore ? rows.subList(0, limit) : rows;
    String nextCursor = hasMore ? encode(items.getLast()) : null;
    return new LedgerTransactionPage(items, nextCursor);
  }

  private static String encode(LedgerTransactionSummary item) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(
        (item.occurredOn() + "|" + item.transactionId()).getBytes(StandardCharsets.UTF_8));
  }

  private static TransactionCursor decode(String encoded) {
    try {
      String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
      String[] parts = value.split("\\|", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException("transaction cursor is malformed");
      }
      return new TransactionCursor(LocalDate.parse(parts[0]), parts[1]);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("transaction cursor is malformed", exception);
    }
  }
}
