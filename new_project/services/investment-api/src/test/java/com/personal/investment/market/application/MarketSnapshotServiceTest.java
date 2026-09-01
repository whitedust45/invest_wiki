package com.personal.investment.market.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.market.domain.InstrumentStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketSnapshotServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String QQQ = "01K8D43J4YFN7X9R2B6C8M0V3Q";
  private static final String QLD = "01K8D43J4YFN7X9R2B6C8M0V3R";
  private static final LocalDate DATE = LocalDate.of(2026, 7, 31);

  @Test
  void preservesReliableImportAndOnlyAddsMissingAutomaticFieldsInTheSameRun() {
    InMemorySnapshotPort snapshots = new InMemorySnapshotPort();
    InMemoryInstrumentPort instruments = new InMemoryInstrumentPort(List.of(
        spot(QQQ, "QQQ", "QQQ"), spot(QLD, "QLD", "QLD")));
    MarketAutomaticSource source = (values, date) -> new MarketAutomaticSource.MarketAutomaticSourceResult(List.of(
        new MarketAutomaticSource.Quote(QQQ, Instant.parse("2026-07-31T08:00:00Z"), "QQQ-auto", 20_001L, 19_900L),
        new MarketAutomaticSource.Quote(QLD, Instant.parse("2026-07-31T08:00:00Z"), "QLD-auto", 10_000L, 9_900L)),
        List.of(), List.of());
    MarketSnapshotService service = new MarketSnapshotService(snapshots, instruments, source, new Ids(),
        Clock.fixed(Instant.parse("2026-07-31T00:15:00Z"), ZoneOffset.UTC));

    MarketSnapshotSubmissionResult result = service.submit(OWNER, new MarketSnapshotSubmissionCommand(DATE,
        "BROKER_EXPORT_ATTESTED", "local://market/20260731.json", List.of(new MarketSnapshotSubmissionCommand.Quote(
            QQQ, Instant.parse("2026-07-31T07:00:00Z"), "QQQ-import", 20_000L, 19_800L, CurrencyCode.USD)),
        List.of(), List.of()));

    assertThat(result.status()).isEqualTo("QUEUED");
    assertThat(service.processQueuedSubmissions()).isEqualTo(1);
    assertThat(snapshots.quotes).hasSize(2);
    assertThat(snapshots.quotes).filteredOn(value -> value.instrumentId().equals(QQQ)).singleElement()
        .satisfies(value -> {
          assertThat(value.priceCent()).isEqualTo(20_000L);
          assertThat(value.sourceName()).isEqualTo("BROKER_EXPORT_ATTESTED");
        });
    assertThat(snapshots.quotes).filteredOn(value -> value.instrumentId().equals(QLD)).singleElement()
        .satisfies(value -> assertThat(value.sourceName()).isEqualTo("TUSHARE_PRO"));
    assertThat(snapshots.runs.get(result.marketSyncRunId()).status()).isEqualTo("SUCCEEDED");
  }

  private static Instrument spot(String id, String symbol, String code) {
    return new Instrument(id, "US", "NASDAQ", symbol, symbol, AssetType.ETF, CurrencyCode.USD, null,
        InstrumentStatus.ACTIVE, null, null, code, null);
  }

  private static final class InMemoryInstrumentPort implements InstrumentPort {
    private final List<Instrument> values;

    private InMemoryInstrumentPort(List<Instrument> values) {
      this.values = values;
    }

    @Override public Optional<Instrument> findByNaturalKey(String market, String exchange, String symbol) { return Optional.empty(); }
    @Override public Optional<Instrument> findById(String instrumentId) {
      return values.stream().filter(value -> value.instrumentId().equals(instrumentId)).findFirst();
    }
    @Override public List<Instrument> findAll() { return values; }
    @Override public void insert(Instrument instrument, String futureContractId, String optionContractId) { }
  }

  private static final class InMemorySnapshotPort implements MarketSnapshotPort {
    private final Map<String, MarketSyncRun> runs = new HashMap<>();
    private final Map<String, StoredSubmission> submissions = new HashMap<>();
    private final List<QuoteFact> quotes = new ArrayList<>();

    @Override public void createRun(MarketSyncRun run, String policy, String triggeredBy) { runs.put(run.marketSyncRunId(), run); }
    @Override public Optional<MarketSyncRun> findRun(String id) { return Optional.ofNullable(runs.get(id)); }
    @Override public Optional<MarketSyncRun> findSucceededRunForTradingDate(LocalDate date) {
      return runs.values().stream().filter(value -> value.tradingDate().equals(date) && value.status().equals("SUCCEEDED")).findFirst();
    }
    @Override public void insertSubmission(StoredSubmission submission) { submissions.put(submission.marketSnapshotSubmissionId(), submission); }
    @Override public List<String> findQueuedSubmissionIds(int limit) {
      return submissions.values().stream().filter(value -> value.status().equals("QUEUED"))
          .map(StoredSubmission::marketSnapshotSubmissionId).toList();
    }
    @Override public boolean claimSubmission(String id, Instant claimedAt) {
      StoredSubmission value = submissions.get(id);
      if (value == null || !value.status().equals("QUEUED")) return false;
      submissions.put(id, new StoredSubmission(value.marketSnapshotSubmissionId(), value.submittedByUserId(),
          value.marketSyncRunId(), value.tradingDate(), value.sourceName(), value.sourceReference(), "RUNNING",
          value.quotes(), value.metrics(), value.basis()));
      return true;
    }
    @Override public void markRunRunning(String id) {
      MarketSyncRun value = runs.get(id);
      runs.put(id, new MarketSyncRun(value.marketSyncRunId(), value.tradingDate(), value.runType(), "RUNNING", value.startedAt(), null));
    }
    @Override public Optional<StoredSubmission> findSubmission(String id) { return Optional.ofNullable(submissions.get(id)); }
    @Override public void markRunCompleted(String id, String status, Instant completedAt) {
      MarketSyncRun value = runs.get(id);
      runs.put(id, new MarketSyncRun(value.marketSyncRunId(), value.tradingDate(), value.runType(), status, value.startedAt(), completedAt));
    }
    @Override public void markSubmissionCompleted(String id, String status, Instant completedAt) {
      StoredSubmission value = submissions.get(id);
      submissions.put(id, new StoredSubmission(value.marketSnapshotSubmissionId(), value.submittedByUserId(),
          value.marketSyncRunId(), value.tradingDate(), value.sourceName(), value.sourceReference(), status,
          value.quotes(), value.metrics(), value.basis()));
    }
    @Override public void appendAttempt(String a, String r, int n, String t, String s, String source, String c, String e, Instant st, Instant end) { }
    @Override public void appendSourceEvent(String id, String run, String instrument, String source, String type, String severity, String code, String summary) { }
    @Override public void appendQuote(QuoteFact fact) { quotes.add(fact); }
    @Override public void appendMetric(MetricFact fact) { }
    @Override public void appendBasis(BasisFact fact) { }
    @Override public List<BigDecimal> findMetricHistory(String instrument, String name, LocalDate start, LocalDate end) { return List.of(); }
  }

  private static final class Ids implements LedgerIdGenerator {
    private int sequence;
    @Override public String next() { return "01K8D43J4YFN7X9R2B6C8M0V" + String.format("%02d", sequence++); }
  }
}
