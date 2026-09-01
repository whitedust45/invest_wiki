package com.personal.investment.market.application;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.Instrument;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a reliable import before using Tushare to fill only absent fields.  Every write is tied to one immutable
 * sync run, so strategy readers never combine data from different runs.
 */
@Service
public class MarketSnapshotService {
  private static final String IMPORT_POLICY_PREFIX = "IMPORT_TUSHARE_V1:";
  private static final String AUTO_POLICY = "TUSHARE_V1";
  private static final int MIN_PB_HISTORY_OBSERVATIONS = 252;
  private final MarketSnapshotPort snapshotPort;
  private final InstrumentPort instrumentPort;
  private final MarketAutomaticSource automaticSource;
  private final LedgerIdGenerator idGenerator;
  private final Clock clock;

  public MarketSnapshotService(MarketSnapshotPort snapshotPort, InstrumentPort instrumentPort,
      MarketAutomaticSource automaticSource, LedgerIdGenerator idGenerator, Clock clock) {
    this.snapshotPort = snapshotPort;
    this.instrumentPort = instrumentPort;
    this.automaticSource = automaticSource;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Transactional
  public MarketSnapshotSubmissionResult submit(String submittedByUserId, MarketSnapshotSubmissionCommand command) {
    requireUlid(submittedByUserId, "submittedByUserId");
    validateCommand(command);
    String submissionId = idGenerator.next();
    String runId = idGenerator.next();
    Instant now = Instant.now(clock);
    snapshotPort.createRun(new MarketSyncRun(runId, command.tradingDate(), "TRUSTED_IMPORT", "QUEUED", now, null),
        IMPORT_POLICY_PREFIX + submissionId, "API_IMPORT");
    snapshotPort.insertSubmission(new MarketSnapshotPort.StoredSubmission(submissionId, submittedByUserId, runId,
        command.tradingDate(), normalized(command.sourceName(), "sourceName", 64),
        normalized(command.sourceReference(), "sourceReference", 512), "QUEUED", command.quotes(), command.metrics(),
        command.basis()));
    return new MarketSnapshotSubmissionResult(submissionId, runId, "QUEUED");
  }

  /** Queue worker entrypoint. It has no dependence on an HTTP request and is safe to invoke repeatedly. */
  @Transactional
  public int processQueuedSubmissions() {
    int processed = 0;
    for (String submissionId : snapshotPort.findQueuedSubmissionIds(20)) {
      if (processSubmission(submissionId)) {
        processed++;
      }
    }
    return processed;
  }

  @Transactional
  public boolean processSubmission(String marketSnapshotSubmissionId) {
    Instant now = Instant.now(clock);
    if (!snapshotPort.claimSubmission(marketSnapshotSubmissionId, now)) {
      return false;
    }
    MarketSnapshotPort.StoredSubmission submission = snapshotPort.findSubmission(marketSnapshotSubmissionId)
        .orElseThrow(() -> new IllegalStateException("claimed market snapshot submission was not found"));
    snapshotPort.markRunRunning(submission.marketSyncRunId());
    applyRun(submission.marketSyncRunId(), submission.tradingDate(), submission.sourceName(), submission.quotes(),
        submission.metrics(), submission.basis(), "IMPORT_WORKER", true);
    snapshotPort.markSubmissionCompleted(submission.marketSnapshotSubmissionId(), "SUCCEEDED", Instant.now(clock));
    return true;
  }

  /** XXL-JOB entrypoint: imports first, then creates exactly one automatic-only run when no successful run exists. */
  @Transactional
  public MarketRefreshResult refreshForTradingDate(LocalDate tradingDate) {
    if (tradingDate == null || tradingDate.isAfter(LocalDate.now(clock))) {
      throw new IllegalArgumentException("tradingDate must not be in the future");
    }
    int imported = processQueuedSubmissions();
    if (snapshotPort.findSucceededRunForTradingDate(tradingDate).isPresent()) {
      return new MarketRefreshResult(tradingDate, imported, false, "IMPORTED_OR_ALREADY_CURRENT");
    }
    String runId = idGenerator.next();
    Instant now = Instant.now(clock);
    snapshotPort.createRun(new MarketSyncRun(runId, tradingDate, "AUTOMATIC_REFRESH", "QUEUED", now, null), AUTO_POLICY,
        "XXL_JOB");
    snapshotPort.markRunRunning(runId);
    applyRun(runId, tradingDate, "TUSHARE_PRO", List.of(), List.of(), List.of(), "XXL_JOB", false);
    return new MarketRefreshResult(tradingDate, imported, true, "AUTOMATIC_REFRESH_COMPLETED");
  }

  public Optional<MarketSyncRun> findRun(String marketSyncRunId) {
    return snapshotPort.findRun(marketSyncRunId);
  }

  private void applyRun(String runId, LocalDate tradingDate, String importSourceName,
      List<MarketSnapshotSubmissionCommand.Quote> importQuotes,
      List<MarketSnapshotSubmissionCommand.Metric> importMetrics,
      List<MarketSnapshotSubmissionCommand.Basis> importBasis, String triggerType, boolean imported) {
    Instant startedAt = Instant.now(clock);
    List<Instrument> instruments = instrumentPort.findAll();
    Map<String, Instrument> byId = indexInstruments(instruments);
    Map<String, MarketSnapshotSubmissionCommand.Quote> quotesByInstrument = new HashMap<>();
    Set<String> importedMetricKeys = new HashSet<>();
    Set<String> importedBasisFutureIds = new HashSet<>();
    int attempt = 1;

    for (MarketSnapshotSubmissionCommand.Quote quote : importQuotes) {
      writeQuote(runId, importSourceName, quote.instrumentId(), quote.quoteTime(), quote.sourceObservationKey(),
          quote.priceCent(), quote.prevCloseCent(), quote.currency());
      quotesByInstrument.put(quote.instrumentId(), quote);
    }
    for (MarketSnapshotSubmissionCommand.Metric metric : importMetrics) {
      writeMetric(runId, tradingDate, importSourceName, metric.instrumentId(), metric.metricName(),
          metric.metricValueDecimal(), metric.sourceObservationKey());
      importedMetricKeys.add(metric.instrumentId() + "|" + metric.metricName());
    }
    for (MarketSnapshotSubmissionCommand.Basis basis : importBasis) {
      writeBasis(runId, tradingDate, importSourceName, basis.underlyingInstrumentId(), basis.futureInstrumentId(),
          basis.spotPricePoints(), basis.futurePricePoints(), basis.annualizedBasisDecimal(), basis.maturityDate(),
          basis.daysLeft(), basis.sourceObservationKey());
      importedBasisFutureIds.add(basis.futureInstrumentId());
    }
    if (imported) {
      snapshotPort.appendAttempt(idGenerator.next(), runId, attempt++, triggerType, "SUCCEEDED", importSourceName,
          null, null, startedAt, Instant.now(clock));
    }

    MarketAutomaticSource.MarketAutomaticSourceResult automatic = automaticSource.refresh(
        instruments.stream().filter(instrument -> instrument.tushareCode() != null).toList(), tradingDate);
    for (MarketAutomaticSource.Quote quote : automatic.quotes()) {
      if (quotesByInstrument.containsKey(quote.instrumentId())) {
        continue;
      }
      Instrument instrument = byId.get(quote.instrumentId());
      if (instrument == null) {
        continue;
      }
      writeQuote(runId, "TUSHARE_PRO", quote.instrumentId(), quote.quoteTime(), quote.sourceObservationKey(),
          quote.priceCent(), quote.prevCloseCent(), instrument.nativeCurrency());
      quotesByInstrument.put(quote.instrumentId(), new MarketSnapshotSubmissionCommand.Quote(quote.instrumentId(),
          quote.quoteTime(), quote.sourceObservationKey(), quote.priceCent(), quote.prevCloseCent(),
          instrument.nativeCurrency()));
    }
    for (MarketAutomaticSource.Metric metric : automatic.metrics()) {
      if (importedMetricKeys.contains(metric.instrumentId() + "|" + metric.metricName())) {
        continue;
      }
      writeMetric(runId, metric.tradeDate(), "TUSHARE_PRO", metric.instrumentId(), metric.metricName(),
          metric.valueDecimal(), metric.sourceObservationKey());
    }
    for (MarketAutomaticSource.Issue issue : automatic.issues()) {
      snapshotPort.appendSourceEvent(idGenerator.next(), runId, issue.instrumentId(), "TUSHARE_PRO", "FETCH_FAILED",
          "WARN", issue.errorCode(), concise(issue.summary(), 1_000));
    }
    snapshotPort.appendAttempt(idGenerator.next(), runId, attempt, triggerType,
        automatic.issues().isEmpty() ? "SUCCEEDED" : "PARTIAL_SUCCEEDED", "TUSHARE_PRO", null, null, startedAt,
        Instant.now(clock));

    deriveMissingBasis(runId, tradingDate, instruments, quotesByInstrument, importedBasisFutureIds);
    derivePbPercentiles(runId, tradingDate, instruments, importedMetricKeys, automatic.metrics());
    snapshotPort.markRunCompleted(runId, "SUCCEEDED", Instant.now(clock));
  }

  private void deriveMissingBasis(String runId, LocalDate tradingDate, List<Instrument> instruments,
      Map<String, MarketSnapshotSubmissionCommand.Quote> quotesByInstrument, Set<String> importedBasisFutureIds) {
    for (Instrument future : instruments) {
      if (future.assetType() != AssetType.FUTURE || importedBasisFutureIds.contains(future.instrumentId())) {
        continue;
      }
      MarketSnapshotSubmissionCommand.Quote underlying = quotesByInstrument.get(future.underlyingInstrumentId());
      MarketSnapshotSubmissionCommand.Quote futureQuote = quotesByInstrument.get(future.instrumentId());
      if (underlying == null || futureQuote == null || future.maturityDate() == null) {
        continue;
      }
      int daysLeft = Math.toIntExact(ChronoUnit.DAYS.between(tradingDate, future.maturityDate()));
      if (daysLeft < 0) {
        continue;
      }
      BigDecimal spot = BigDecimal.valueOf(underlying.priceCent(), 2);
      BigDecimal price = BigDecimal.valueOf(futureQuote.priceCent(), 2);
      BigDecimal annualized = daysLeft == 0 ? null : price.subtract(spot)
          .divide(spot, 12, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(365))
          .divide(BigDecimal.valueOf(daysLeft), 12, RoundingMode.HALF_UP);
      writeBasis(runId, tradingDate, "DERIVED_TUSHARE_PRO", future.underlyingInstrumentId(), future.instrumentId(),
          spot, price, annualized, future.maturityDate(), daysLeft,
          future.instrumentId() + "-derived-basis-" + tradingDate);
    }
  }

  private void derivePbPercentiles(String runId, LocalDate tradingDate, List<Instrument> instruments,
      Set<String> importedMetricKeys, List<MarketAutomaticSource.Metric> automaticMetrics) {
    for (Instrument instrument : instruments) {
      if (instrument.assetType() != AssetType.INDEX
          || importedMetricKeys.contains(instrument.instrumentId() + "|PB_PERCENTILE")) {
        continue;
      }
      List<BigDecimal> values = snapshotPort.findMetricHistory(instrument.instrumentId(), "PB",
          tradingDate.minusYears(10), tradingDate);
      if (values.size() < MIN_PB_HISTORY_OBSERVATIONS) {
        continue;
      }
      BigDecimal current = automaticMetrics.stream()
          .filter(value -> value.instrumentId().equals(instrument.instrumentId()) && value.metricName().equals("PB")
              && value.tradeDate().equals(tradingDate))
          .map(MarketAutomaticSource.Metric::valueDecimal).findFirst().orElse(null);
      if (current == null) {
        continue;
      }
      long atOrBelow = values.stream().filter(value -> value.compareTo(current) <= 0).count();
      BigDecimal percentile = BigDecimal.valueOf(atOrBelow).multiply(BigDecimal.valueOf(100))
          .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
      writeMetric(runId, tradingDate, "DERIVED_LOCAL_HISTORY", instrument.instrumentId(), "PB_PERCENTILE", percentile,
          instrument.instrumentId() + "-pb-percentile-" + tradingDate);
    }
  }

  private void writeQuote(String runId, String sourceName, String instrumentId, Instant quoteTime,
      String observationKey, long priceCent, Long prevCloseCent, CurrencyCode currency) {
    snapshotPort.appendQuote(new MarketSnapshotPort.QuoteFact(idGenerator.next(), instrumentId, runId, quoteTime,
        sourceName, observationKey, priceCent, prevCloseCent, currency.name(), hash("Q", instrumentId, quoteTime.toString(),
        sourceName, observationKey, Long.toString(priceCent), Objects.toString(prevCloseCent, ""), currency.name())));
  }

  private void writeMetric(String runId, LocalDate tradeDate, String sourceName, String instrumentId,
      String metricName, BigDecimal value, String observationKey) {
    snapshotPort.appendMetric(new MarketSnapshotPort.MetricFact(idGenerator.next(), instrumentId, runId, tradeDate,
        metricName, sourceName, value, hash("M", instrumentId, tradeDate.toString(), sourceName, metricName,
            value.toPlainString(), observationKey)));
  }

  private void writeBasis(String runId, LocalDate tradeDate, String sourceName, String underlyingInstrumentId,
      String futureInstrumentId, BigDecimal spot, BigDecimal future, BigDecimal annualized, LocalDate maturityDate,
      Integer daysLeft, String observationKey) {
    snapshotPort.appendBasis(new MarketSnapshotPort.BasisFact(idGenerator.next(), underlyingInstrumentId,
        futureInstrumentId, runId, tradeDate, sourceName, spot, future, future.subtract(spot), annualized, maturityDate,
        daysLeft, hash("B", underlyingInstrumentId, futureInstrumentId, tradeDate.toString(), sourceName, spot.toPlainString(),
            future.toPlainString(), Objects.toString(annualized, ""), Objects.toString(maturityDate, ""),
            Objects.toString(daysLeft, ""), observationKey)));
  }

  private void validateCommand(MarketSnapshotSubmissionCommand command) {
    if (command == null || command.tradingDate() == null || command.tradingDate().isAfter(LocalDate.now(clock))) {
      throw new MarketSnapshotValidationException("tradingDate must not be in the future");
    }
    normalized(command.sourceName(), "sourceName", 64);
    normalized(command.sourceReference(), "sourceReference", 512);
    if ((command.quotes() == null ? 0 : command.quotes().size()) + (command.metrics() == null ? 0 : command.metrics().size())
        + (command.basis() == null ? 0 : command.basis().size()) == 0) {
      throw new MarketSnapshotValidationException("at least one observation is required");
    }
    Map<String, Instrument> instruments = indexInstruments(instrumentPort.findAll());
    Set<String> quoteKeys = new HashSet<>();
    for (MarketSnapshotSubmissionCommand.Quote quote : safe(command.quotes())) {
      Instrument instrument = requiredInstrument(instruments, quote.instrumentId());
      if (quote.quoteTime() == null || quote.priceCent() <= 0 || quote.prevCloseCent() != null && quote.prevCloseCent() <= 0
          || quote.currency() != instrument.nativeCurrency() || !quoteKeys.add(quote.instrumentId() + "|"
          + normalized(quote.sourceObservationKey(), "sourceObservationKey", 256))) {
        throw new MarketSnapshotValidationException("quote is invalid or duplicated");
      }
    }
    Set<String> metricKeys = new HashSet<>();
    for (MarketSnapshotSubmissionCommand.Metric metric : safe(command.metrics())) {
      requiredInstrument(instruments, metric.instrumentId());
      String name = normalized(metric.metricName(), "metricName", 64);
      if (metric.metricValueDecimal() == null || metric.metricValueDecimal().scale() > 12 || !metricKeys.add(
          metric.instrumentId() + "|" + name + "|" + normalized(metric.sourceObservationKey(), "sourceObservationKey", 256))
          || ("PB_PERCENTILE".equals(name) && (metric.metricValueDecimal().compareTo(BigDecimal.ZERO) < 0
          || metric.metricValueDecimal().compareTo(BigDecimal.valueOf(100)) > 0))) {
        throw new MarketSnapshotValidationException("metric is invalid or duplicated");
      }
    }
    Set<String> basisKeys = new HashSet<>();
    for (MarketSnapshotSubmissionCommand.Basis basis : safe(command.basis())) {
      Instrument underlying = requiredInstrument(instruments, basis.underlyingInstrumentId());
      Instrument future = requiredInstrument(instruments, basis.futureInstrumentId());
      if (underlying.assetType() != AssetType.INDEX || future.assetType() != AssetType.FUTURE
          || !underlying.instrumentId().equals(future.underlyingInstrumentId()) || basis.spotPricePoints() == null
          || basis.futurePricePoints() == null || basis.spotPricePoints().signum() <= 0
          || basis.futurePricePoints().signum() <= 0 || !basisKeys.add(basis.futureInstrumentId() + "|"
          + normalized(basis.sourceObservationKey(), "sourceObservationKey", 256))
          || (basis.maturityDate() != null && (basis.daysLeft() == null || basis.daysLeft() < 0
          || ChronoUnit.DAYS.between(command.tradingDate(), basis.maturityDate()) != basis.daysLeft()))) {
        throw new MarketSnapshotValidationException("basis is invalid or duplicated");
      }
    }
  }

  private static Map<String, Instrument> indexInstruments(List<Instrument> instruments) {
    Map<String, Instrument> indexed = new HashMap<>();
    for (Instrument instrument : instruments) {
      indexed.put(instrument.instrumentId(), instrument);
    }
    return indexed;
  }

  private static Instrument requiredInstrument(Map<String, Instrument> instruments, String instrumentId) {
    requireUlid(instrumentId, "instrumentId");
    Instrument instrument = instruments.get(instrumentId);
    if (instrument == null) {
      throw new MarketSnapshotValidationException("instrument was not found");
    }
    return instrument;
  }

  private static <T> List<T> safe(List<T> source) {
    return source == null ? List.of() : source;
  }

  private static String normalized(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new MarketSnapshotValidationException(field + " must not be blank");
    }
    String result = value.trim();
    if (result.length() > maxLength) {
      throw new MarketSnapshotValidationException(field + " exceeds " + maxLength + " characters");
    }
    return result;
  }

  private static void requireUlid(String value, String field) {
    if (value == null || !value.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
      throw new MarketSnapshotValidationException(field + " must be a ULID");
    }
  }

  private static byte[] hash(String... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String value : values) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
      }
      return digest.digest();
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String concise(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return "market source failed without detail";
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  public record MarketRefreshResult(LocalDate tradingDate, int processedImports, boolean automaticRunCreated,
                                    String status) {
  }
}
