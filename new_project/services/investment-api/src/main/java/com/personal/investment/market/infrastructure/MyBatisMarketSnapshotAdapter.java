package com.personal.investment.market.infrastructure;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.application.MarketSnapshotPort;
import com.personal.investment.market.application.MarketSnapshotSubmissionCommand;
import com.personal.investment.market.application.MarketSyncRun;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MyBatisMarketSnapshotAdapter implements MarketSnapshotPort {
  private final MarketSnapshotMapper mapper;
  private final LedgerIdGenerator idGenerator;

  public MyBatisMarketSnapshotAdapter(MarketSnapshotMapper mapper, LedgerIdGenerator idGenerator) {
    this.mapper = mapper;
    this.idGenerator = idGenerator;
  }

  @Override
  public void createRun(MarketSyncRun run, String sourcePolicyVersion, String triggeredBy) {
    requireOne(mapper.insertRun(run.marketSyncRunId(), run.runType(), run.tradingDate(), sourcePolicyVersion,
        run.status(), triggeredBy, run.startedAt()), "market sync run");
  }

  @Override
  public Optional<MarketSyncRun> findRun(String marketSyncRunId) {
    return Optional.ofNullable(mapper.findRun(marketSyncRunId)).map(this::toRun);
  }

  @Override
  public Optional<MarketSyncRun> findSucceededRunForTradingDate(LocalDate tradingDate) {
    return Optional.ofNullable(mapper.findSucceededRunForTradingDate(tradingDate)).map(this::toRun);
  }

  @Override
  public void insertSubmission(StoredSubmission submission) {
    requireOne(mapper.insertSubmission(submission.marketSnapshotSubmissionId(), submission.submittedByUserId(),
        submission.marketSyncRunId(), submission.tradingDate(), submission.sourceName(), submission.sourceReference(),
        submission.status()), "market snapshot submission");
    for (MarketSnapshotSubmissionCommand.Quote quote : submission.quotes()) {
      requireOne(mapper.insertSubmissionQuote(idGenerator.next(), submission.marketSnapshotSubmissionId(),
          quote.instrumentId(), quote.quoteTime(), quote.sourceObservationKey(), quote.priceCent(),
          quote.prevCloseCent(), quote.currency().name()), "market snapshot submission quote");
    }
    for (MarketSnapshotSubmissionCommand.Metric metric : submission.metrics()) {
      requireOne(mapper.insertSubmissionMetric(idGenerator.next(), submission.marketSnapshotSubmissionId(),
          metric.instrumentId(), metric.metricName(), metric.metricValueDecimal(), metric.sourceObservationKey()),
          "market snapshot submission metric");
    }
    for (MarketSnapshotSubmissionCommand.Basis basis : submission.basis()) {
      requireOne(mapper.insertSubmissionBasis(idGenerator.next(), submission.marketSnapshotSubmissionId(),
          basis.underlyingInstrumentId(), basis.futureInstrumentId(), basis.spotPricePoints(), basis.futurePricePoints(),
          basis.annualizedBasisDecimal(), basis.maturityDate(), basis.daysLeft(), basis.sourceObservationKey()),
          "market snapshot submission basis");
    }
  }

  @Override
  public List<String> findQueuedSubmissionIds(int limit) {
    return mapper.findQueuedSubmissionIds(limit);
  }

  @Override
  public boolean claimSubmission(String marketSnapshotSubmissionId, Instant claimedAt) {
    return mapper.claimSubmission(marketSnapshotSubmissionId, claimedAt) == 1;
  }

  @Override
  public void markRunRunning(String marketSyncRunId) {
    requireOne(mapper.markRunRunning(marketSyncRunId), "market sync run claim");
  }

  @Override
  public Optional<StoredSubmission> findSubmission(String marketSnapshotSubmissionId) {
    MarketSnapshotMapper.SubmissionRow row = mapper.findSubmission(marketSnapshotSubmissionId);
    if (row == null) {
      return Optional.empty();
    }
    List<MarketSnapshotSubmissionCommand.Quote> quotes = mapper.findSubmissionQuotes(marketSnapshotSubmissionId)
        .stream().map(value -> new MarketSnapshotSubmissionCommand.Quote(value.instrumentId(), value.quoteTime(),
            value.sourceObservationKey(), value.priceCent(), value.prevCloseCent(), CurrencyCode.of(value.currency())))
        .toList();
    List<MarketSnapshotSubmissionCommand.Metric> metrics = mapper.findSubmissionMetrics(marketSnapshotSubmissionId)
        .stream().map(value -> new MarketSnapshotSubmissionCommand.Metric(value.instrumentId(), value.metricName(),
            value.metricValueDecimal(), value.sourceObservationKey())).toList();
    List<MarketSnapshotSubmissionCommand.Basis> basis = mapper.findSubmissionBasis(marketSnapshotSubmissionId)
        .stream().map(value -> new MarketSnapshotSubmissionCommand.Basis(value.underlyingInstrumentId(),
            value.futureInstrumentId(), value.spotPricePoints(), value.futurePricePoints(),
            value.annualizedBasisDecimal(), value.maturityDate(), value.daysLeft(), value.sourceObservationKey()))
        .toList();
    return Optional.of(new StoredSubmission(row.marketSnapshotSubmissionId(), row.submittedByUserId(),
        row.marketSyncRunId(), row.tradingDate(), row.sourceName(), row.sourceReference(), row.status(), quotes, metrics,
        basis));
  }

  @Override
  public void markRunCompleted(String marketSyncRunId, String status, Instant completedAt) {
    requireOne(mapper.markRunCompleted(marketSyncRunId, status, completedAt), "market sync run completion");
  }

  @Override
  public void markSubmissionCompleted(String marketSnapshotSubmissionId, String status, Instant completedAt) {
    requireOne(mapper.markSubmissionCompleted(marketSnapshotSubmissionId, status, completedAt),
        "market snapshot submission completion");
  }

  @Override
  public void appendAttempt(String marketSyncAttemptId, String marketSyncRunId, int attemptNo, String triggerType,
      String status, String sourceName, String errorCode, String errorSummary, Instant startedAt, Instant completedAt) {
    requireOne(mapper.insertAttempt(marketSyncAttemptId, marketSyncRunId, attemptNo, triggerType, status, sourceName,
        errorCode, errorSummary, startedAt, completedAt), "market sync attempt");
  }

  @Override
  public void appendSourceEvent(String marketSourceEventId, String marketSyncRunId, String instrumentId,
      String sourceName, String eventType, String severity, String errorCode, String errorSummary) {
    requireOne(mapper.insertSourceEvent(marketSourceEventId, marketSyncRunId, instrumentId, sourceName, eventType,
        severity, errorCode, errorSummary), "market source event");
  }

  @Override
  public void appendQuote(QuoteFact fact) {
    requireOne(mapper.insertQuote(fact.quoteSnapshotId(), fact.instrumentId(), fact.marketSyncRunId(), fact.quoteTime(),
        fact.sourceName(), fact.sourceObservationKey(), fact.observationHash(), fact.priceCent(), fact.prevCloseCent(),
        fact.currency()), "quote snapshot");
  }

  @Override
  public void appendMetric(MetricFact fact) {
    requireOne(mapper.insertMetric(fact.dailyMetricId(), fact.instrumentId(), fact.marketSyncRunId(), fact.tradeDate(),
        fact.metricName(), fact.sourceName(), fact.valueDecimal(), fact.observationHash()), "daily metric");
  }

  @Override
  public void appendBasis(BasisFact fact) {
    requireOne(mapper.insertBasis(fact.basisSnapshotId(), fact.underlyingInstrumentId(), fact.futureInstrumentId(),
        fact.marketSyncRunId(), fact.tradeDate(), fact.sourceName(), fact.spotPricePoints(), fact.futurePricePoints(),
        fact.basisPoints(), fact.annualizedBasisDecimal(), fact.maturityDate(), fact.daysLeft(), fact.observationHash()),
        "basis snapshot");
  }

  @Override
  public List<BigDecimal> findMetricHistory(String instrumentId, String metricName, LocalDate startDate,
      LocalDate endDate) {
    return mapper.findMetricHistory(instrumentId, metricName, startDate, endDate);
  }

  private MarketSyncRun toRun(MarketSnapshotMapper.RunRow row) {
    return new MarketSyncRun(row.marketSyncRunId(), row.tradingDate(), row.runType(), row.status(), row.startedAt(),
        row.completedAt());
  }

  private static void requireOne(int changed, String label) {
    if (changed != 1) {
      throw new IllegalStateException(label + " was not persisted");
    }
  }
}
