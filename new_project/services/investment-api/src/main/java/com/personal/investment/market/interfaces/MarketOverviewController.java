package com.personal.investment.market.interfaces;

import com.personal.investment.market.application.MarketOverview;
import com.personal.investment.market.application.MarketOverviewService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Native-client market workspace; values are source-attributed and never calculated in the mini program. */
@RestController
@RequestMapping("/api/v1/market/overview")
public class MarketOverviewController {
  private final MarketOverviewService service;

  public MarketOverviewController(MarketOverviewService service) {
    this.service = service;
  }

  @GetMapping
  public MarketOverviewResponse overview(Authentication authentication) {
    // Authentication is intentionally required by the global security policy even though market facts are shared.
    MarketOverview overview = service.overview();
    return new MarketOverviewResponse(run(overview.latestRun()), overview.quotes().stream().map(this::quote).toList(),
        overview.metrics().stream().map(this::metric).toList(), overview.basis().stream().map(this::basis).toList(),
        overview.sourceEvents().stream().map(this::event).toList());
  }

  private MarketRunResponse run(MarketOverview.MarketRun value) {
    return value == null ? null : new MarketRunResponse(value.marketSyncRunId(), value.tradingDate(), value.runType(),
        value.status(), value.triggeredBy(), value.startedAt(), value.completedAt(),
        value.attempts().stream().map(attempt -> new MarketAttemptResponse(attempt.marketSyncAttemptId(),
            attempt.attemptNo(), attempt.triggerType(), attempt.status(), attempt.sourceName(), attempt.errorCode(),
            attempt.errorSummary(), attempt.startedAt(), attempt.completedAt())).toList());
  }

  private MarketQuoteResponse quote(MarketOverview.MarketQuote value) {
    return new MarketQuoteResponse(value.quoteSnapshotId(), value.instrumentId(), value.symbol(), value.displayName(),
        value.currency().name(), Long.toString(value.priceCent()), optionalCent(value.prevCloseCent()), value.quoteTime(),
        value.sourceName());
  }

  private MarketMetricResponse metric(MarketOverview.MarketMetric value) {
    return new MarketMetricResponse(value.dailyMetricId(), value.instrumentId(), value.symbol(), value.displayName(),
        value.tradeDate(), value.metricName(), decimal(value.valueDecimal()), optionalCent(value.valueCent()),
        value.currency() == null ? null : value.currency().name(), value.sourceName());
  }

  private MarketBasisResponse basis(MarketOverview.MarketBasis value) {
    return new MarketBasisResponse(value.basisSnapshotId(), value.underlyingInstrumentId(), value.underlyingSymbol(),
        value.futureInstrumentId(), value.futureSymbol(), value.productCode(), value.tradeDate(),
        decimal(value.spotPricePoints()), decimal(value.futurePricePoints()), decimal(value.basisPoints()),
        decimal(value.annualizedBasisDecimal()), value.maturityDate(), value.daysLeft(), value.sourceName());
  }

  private MarketSourceEventResponse event(MarketOverview.MarketSourceEvent value) {
    return new MarketSourceEventResponse(value.marketSourceEventId(), value.instrumentId(), value.symbol(),
        value.sourceName(), value.eventType(), value.severity(), value.errorCode(), value.errorSummary(),
        value.createdAt());
  }

  private static String optionalCent(Long value) {
    return value == null ? null : Long.toString(value);
  }

  private static String decimal(BigDecimal value) {
    return value == null ? null : value.stripTrailingZeros().toPlainString();
  }

  public record MarketOverviewResponse(MarketRunResponse latestRun, List<MarketQuoteResponse> quotes,
                                      List<MarketMetricResponse> metrics, List<MarketBasisResponse> basis,
                                      List<MarketSourceEventResponse> sourceEvents) {
    public MarketOverviewResponse {
      quotes = List.copyOf(quotes);
      metrics = List.copyOf(metrics);
      basis = List.copyOf(basis);
      sourceEvents = List.copyOf(sourceEvents);
    }
  }

  public record MarketRunResponse(String marketSyncRunId, LocalDate tradingDate, String runType, String status,
                                  String triggeredBy, Instant startedAt, Instant completedAt,
                                  List<MarketAttemptResponse> attempts) {
    public MarketRunResponse {
      attempts = List.copyOf(attempts);
    }
  }

  public record MarketAttemptResponse(String marketSyncAttemptId, int attemptNo, String triggerType, String status,
                                      String sourceName, String errorCode, String errorSummary, Instant startedAt,
                                      Instant completedAt) {
  }

  public record MarketQuoteResponse(String quoteSnapshotId, String instrumentId, String symbol, String displayName,
                                    String currency, String priceCent, String prevCloseCent, Instant quoteTime,
                                    String sourceName) {
  }

  public record MarketMetricResponse(String dailyMetricId, String instrumentId, String symbol, String displayName,
                                     LocalDate tradeDate, String metricName, String valueDecimal, String valueCent,
                                     String currency, String sourceName) {
  }

  public record MarketBasisResponse(String basisSnapshotId, String underlyingInstrumentId, String underlyingSymbol,
                                    String futureInstrumentId, String futureSymbol, String productCode,
                                    LocalDate tradeDate, String spotPricePoints, String futurePricePoints,
                                    String basisPoints, String annualizedBasisDecimal, LocalDate maturityDate,
                                    Integer daysLeft, String sourceName) {
  }

  public record MarketSourceEventResponse(String marketSourceEventId, String instrumentId, String symbol,
                                          String sourceName, String eventType, String severity, String errorCode,
                                          String errorSummary, Instant createdAt) {
  }
}
