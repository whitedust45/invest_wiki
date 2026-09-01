package com.personal.investment.reporting.application;

import com.personal.investment.identity.domain.UlidGenerator;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.portfolio.application.PortfolioCurrencyOverview;
import com.personal.investment.portfolio.application.PortfolioOverviewService;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records only fully valued snapshots.  A missing or non-representable valuation deliberately leaves a chart gap;
 * it is never replaced with cost, a mixed-currency value, or a rounded figure.
 */
@Service
public class PortfolioHistoryService {
  private static final int MAX_POINTS = 1_000;

  private final PortfolioOverviewService portfolioOverviewService;
  private final PortfolioHistorySnapshotPort historyPort;
  private final Clock clock;

  public PortfolioHistoryService(PortfolioOverviewService portfolioOverviewService,
      PortfolioHistorySnapshotPort historyPort, Clock clock) {
    this.portfolioOverviewService = portfolioOverviewService;
    this.historyPort = historyPort;
    this.clock = clock;
  }

  @Transactional
  public SnapshotWriteResult snapshot(String ownerUserId, LocalDate asOfDate) {
    requireOwner(ownerUserId);
    Objects.requireNonNull(asOfDate, "asOfDate must not be null");
    int persisted = 0;
    int skipped = 0;
    for (PortfolioCurrencyOverview overview : portfolioOverviewService.summary(ownerUserId, asOfDate).items()) {
      if (overview.netAssetCent() == null || overview.marketValueCent() == null
          || (overview.valuationStatus() != com.personal.investment.portfolio.application.PortfolioValuationStatus.MANUAL
          && overview.valuationStatus()
          != com.personal.investment.portfolio.application.PortfolioValuationStatus.NO_OPEN_POSITION)) {
        skipped++;
        continue;
      }
      if (historyPort.exists(ownerUserId, overview.currency(), asOfDate, overview.sourceLedgerVersion())) {
        continue;
      }
      PortfolioHistoryPoint point = PortfolioHistoryPoint.fromValuedOverview(UlidGenerator.next(), overview.currency(),
          asOfDate, overview.netAssetCent(), overview.cashCent(), overview.marketValueCent(),
          overview.sourceLedgerVersion(), clock.instant(), overview.valuationStatus());
      historyPort.append(ownerUserId, point);
      persisted++;
    }
    return new SnapshotWriteResult(persisted, skipped);
  }

  @Transactional(readOnly = true)
  public List<PortfolioHistoryPoint> list(String ownerUserId, CurrencyCode currency, LocalDate fromInclusive,
      LocalDate toInclusive, int limit) {
    requireOwner(ownerUserId);
    Objects.requireNonNull(currency, "currency must not be null");
    if (fromInclusive == null || toInclusive == null || fromInclusive.isAfter(toInclusive)) {
      throw new IllegalArgumentException("history date range is invalid");
    }
    if (limit < 1 || limit > MAX_POINTS) {
      throw new IllegalArgumentException("history limit must be between 1 and " + MAX_POINTS);
    }
    return historyPort.list(ownerUserId, currency, fromInclusive, toInclusive, limit);
  }

  /**
   * Produces a rendering coordinate, not a money value. Keeping this normalization server-side protects the
   * minor-unit convention from JavaScript number precision loss and makes the chart explicitly single-currency.
   */
  public List<PortfolioHistoryChartPoint> chart(List<PortfolioHistoryPoint> items) {
    if (items == null || items.isEmpty()) {
      return List.of();
    }
    long min = items.stream().mapToLong(PortfolioHistoryPoint::netAssetCent).min().orElseThrow();
    long max = items.stream().mapToLong(PortfolioHistoryPoint::netAssetCent).max().orElseThrow();
    if (min == max) {
      return items.stream().map(item -> new PortfolioHistoryChartPoint(item.dailySnapshotId(), item.asOfDate(), 5_000))
          .toList();
    }
    BigInteger minValue = BigInteger.valueOf(min);
    BigInteger range = BigInteger.valueOf(max).subtract(minValue);
    return items.stream().map(item -> new PortfolioHistoryChartPoint(item.dailySnapshotId(), item.asOfDate(),
        BigInteger.valueOf(item.netAssetCent()).subtract(minValue).multiply(BigInteger.valueOf(10_000L))
            .divide(range).intValueExact())).toList();
  }

  @Transactional
  public int snapshotAllOwners(LocalDate asOfDate) {
    Objects.requireNonNull(asOfDate, "asOfDate must not be null");
    int persisted = 0;
    for (String ownerUserId : historyPort.ownersWithLedger()) {
      persisted += snapshot(ownerUserId, asOfDate).persistedCount();
    }
    return persisted;
  }

  private static void requireOwner(String ownerUserId) {
    if (ownerUserId == null || !ownerUserId.matches("^[0-9A-HJKMNP-TV-Z]{26}$")) {
      throw new IllegalArgumentException("ownerUserId must be a ULID");
    }
  }

  public record SnapshotWriteResult(int persistedCount, int skippedUnvaluedCurrencyCount) {
  }
}
