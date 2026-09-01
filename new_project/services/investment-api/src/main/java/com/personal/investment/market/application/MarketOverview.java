package com.personal.investment.market.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Read-only market workspace data. Values retain their original currency and source. */
public record MarketOverview(MarketRun latestRun, List<MarketQuote> quotes, List<MarketMetric> metrics,
                             List<MarketBasis> basis, List<MarketSourceEvent> sourceEvents) {
  public MarketOverview {
    quotes = List.copyOf(quotes);
    metrics = List.copyOf(metrics);
    basis = List.copyOf(basis);
    sourceEvents = List.copyOf(sourceEvents);
  }

  public record MarketRun(String marketSyncRunId, LocalDate tradingDate, String runType, String status,
                          String triggeredBy, Instant startedAt, Instant completedAt, List<MarketAttempt> attempts) {
    public MarketRun {
      attempts = List.copyOf(attempts);
    }
  }

  public record MarketAttempt(String marketSyncAttemptId, int attemptNo, String triggerType, String status,
                              String sourceName, String errorCode, String errorSummary, Instant startedAt,
                              Instant completedAt) {
  }

  public record MarketQuote(String quoteSnapshotId, String instrumentId, String symbol, String displayName,
                            CurrencyCode currency, long priceCent, Long prevCloseCent, Instant quoteTime,
                            String sourceName) {
  }

  public record MarketMetric(String dailyMetricId, String instrumentId, String symbol, String displayName,
                             LocalDate tradeDate, String metricName, BigDecimal valueDecimal, Long valueCent,
                             CurrencyCode currency, String sourceName) {
    public MarketMetric {
      if ((valueDecimal == null) == (valueCent == null)) {
        throw new IllegalArgumentException("exactly one metric representation is required");
      }
    }
  }

  public record MarketBasis(String basisSnapshotId, String underlyingInstrumentId, String underlyingSymbol,
                            String futureInstrumentId, String futureSymbol, String productCode, LocalDate tradeDate,
                            BigDecimal spotPricePoints, BigDecimal futurePricePoints, BigDecimal basisPoints,
                            BigDecimal annualizedBasisDecimal, LocalDate maturityDate, Integer daysLeft,
                            String sourceName) {
  }

  public record MarketSourceEvent(String marketSourceEventId, String instrumentId, String symbol, String sourceName,
                                  String eventType, String severity, String errorCode, String errorSummary,
                                  Instant createdAt) {
  }
}
