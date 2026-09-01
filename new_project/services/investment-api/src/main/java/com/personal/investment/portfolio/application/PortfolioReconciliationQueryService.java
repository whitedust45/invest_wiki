package com.personal.investment.portfolio.application;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioReconciliationQueryService {
  private static final int MAX_LIMIT = 100;
  private final PortfolioReconciliationQueryPort queryPort;

  public PortfolioReconciliationQueryService(PortfolioReconciliationQueryPort queryPort) {
    this.queryPort = queryPort;
  }

  @Transactional(readOnly = true)
  public PortfolioReconciliationPage list(String ownerUserId, String cursorValue, int limit, String cashAccountId,
      LocalDate from, LocalDate to) {
    if (ownerUserId == null || ownerUserId.isBlank() || limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("reconciliation query is invalid");
    }
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("reconciliation from date must not be after to date");
    }
    ReconciliationCursor cursor = cursorValue == null || cursorValue.isBlank() ? null : decode(cursorValue);
    List<PortfolioReconciliationView> rows = queryPort.find(ownerUserId, cursor, limit + 1, cashAccountId, from, to);
    boolean hasMore = rows.size() > limit;
    List<PortfolioReconciliationView> items = hasMore ? rows.subList(0, limit) : rows;
    return new PortfolioReconciliationPage(items, hasMore ? encode(items.getLast()) : null);
  }

  private static String encode(PortfolioReconciliationView view) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString((view.reconciliationDate() + "|"
        + view.sourceLedgerVersion() + "|" + view.createdAt() + "|" + view.reconciliationId())
        .getBytes(StandardCharsets.UTF_8));
  }

  private static ReconciliationCursor decode(String encoded) {
    try {
      String[] parts = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\|", -1);
      if (parts.length != 4) {
        throw new IllegalArgumentException("cursor parts are invalid");
      }
      return new ReconciliationCursor(LocalDate.parse(parts[0]), Long.parseLong(parts[1]),
          java.time.LocalDateTime.parse(parts[2]), parts[3]);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("reconciliation cursor is malformed", exception);
    }
  }
}
