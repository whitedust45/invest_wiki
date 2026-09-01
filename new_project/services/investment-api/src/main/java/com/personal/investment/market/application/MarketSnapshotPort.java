package com.personal.investment.market.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Append-only storage boundary for market sync audit facts and queued trusted submissions. */
public interface MarketSnapshotPort {
  void createRun(MarketSyncRun run, String sourcePolicyVersion, String triggeredBy);

  Optional<MarketSyncRun> findRun(String marketSyncRunId);

  Optional<MarketSyncRun> findSucceededRunForTradingDate(LocalDate tradingDate);

  void insertSubmission(StoredSubmission submission);

  List<String> findQueuedSubmissionIds(int limit);

  boolean claimSubmission(String marketSnapshotSubmissionId, Instant claimedAt);

  void markRunRunning(String marketSyncRunId);

  Optional<StoredSubmission> findSubmission(String marketSnapshotSubmissionId);

  void markRunCompleted(String marketSyncRunId, String status, Instant completedAt);

  void markSubmissionCompleted(String marketSnapshotSubmissionId, String status, Instant completedAt);

  void appendAttempt(String marketSyncAttemptId, String marketSyncRunId, int attemptNo, String triggerType,
                     String status, String sourceName, String errorCode, String errorSummary, Instant startedAt,
                     Instant completedAt);

  void appendSourceEvent(String marketSourceEventId, String marketSyncRunId, String instrumentId, String sourceName,
                         String eventType, String severity, String errorCode, String errorSummary);

  void appendQuote(QuoteFact fact);

  void appendMetric(MetricFact fact);

  void appendBasis(BasisFact fact);

  List<BigDecimal> findMetricHistory(String instrumentId, String metricName, LocalDate startDate, LocalDate endDate);

  record StoredSubmission(String marketSnapshotSubmissionId, String submittedByUserId, String marketSyncRunId,
                          LocalDate tradingDate, String sourceName, String sourceReference, String status,
                          List<MarketSnapshotSubmissionCommand.Quote> quotes,
                          List<MarketSnapshotSubmissionCommand.Metric> metrics,
                          List<MarketSnapshotSubmissionCommand.Basis> basis) {
    public StoredSubmission {
      quotes = List.copyOf(quotes);
      metrics = List.copyOf(metrics);
      basis = List.copyOf(basis);
    }
  }

  record QuoteFact(String quoteSnapshotId, String instrumentId, String marketSyncRunId, Instant quoteTime,
                   String sourceName, String sourceObservationKey, long priceCent, Long prevCloseCent,
                   String currency, byte[] observationHash) {
  }

  record MetricFact(String dailyMetricId, String instrumentId, String marketSyncRunId, LocalDate tradeDate,
                    String metricName, String sourceName, BigDecimal valueDecimal, byte[] observationHash) {
  }

  record BasisFact(String basisSnapshotId, String underlyingInstrumentId, String futureInstrumentId,
                   String marketSyncRunId, LocalDate tradeDate, String sourceName, BigDecimal spotPricePoints,
                   BigDecimal futurePricePoints, BigDecimal basisPoints, BigDecimal annualizedBasisDecimal,
                   LocalDate maturityDate, Integer daysLeft, byte[] observationHash) {
  }
}
