package com.personal.investment.market.infrastructure;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.application.MarketOverview;
import com.personal.investment.market.application.MarketOverviewPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MyBatisMarketOverviewAdapter implements MarketOverviewPort {
  private final MarketOverviewMapper mapper;

  public MyBatisMarketOverviewAdapter(MarketOverviewMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<MarketOverview.MarketRun> latestRun() {
    return Optional.ofNullable(mapper.latestRun()).map(this::run);
  }

  @Override
  public List<MarketOverview.MarketAttempt> attempts(String marketSyncRunId) {
    return mapper.attempts(marketSyncRunId).stream().map(this::attempt).toList();
  }

  @Override
  public List<MarketOverview.MarketQuote> currentQuotes(String marketSyncRunId) {
    return mapper.currentQuotes(marketSyncRunId).stream().map(value -> new MarketOverview.MarketQuote(
        value.quoteSnapshotId(), value.instrumentId(), value.symbol(), value.displayName(),
        CurrencyCode.of(value.currency()), value.priceCent(), value.prevCloseCent(), value.quoteTime(),
        value.sourceName())).toList();
  }

  @Override
  public List<MarketOverview.MarketMetric> currentMetrics(String marketSyncRunId) {
    return mapper.currentMetrics(marketSyncRunId).stream().map(value -> new MarketOverview.MarketMetric(
        value.dailyMetricId(), value.instrumentId(), value.symbol(), value.displayName(), value.tradeDate(),
        value.metricName(), value.valueDecimal(), value.valueCent(),
        value.currency() == null ? null : CurrencyCode.of(value.currency()), value.sourceName())).toList();
  }

  @Override
  public List<MarketOverview.MarketBasis> currentBasis(String marketSyncRunId) {
    return mapper.currentBasis(marketSyncRunId).stream().map(value -> new MarketOverview.MarketBasis(
        value.basisSnapshotId(), value.underlyingInstrumentId(), value.underlyingSymbol(), value.futureInstrumentId(),
        value.futureSymbol(), value.productCode(), value.tradeDate(), value.spotPricePoints(),
        value.futurePricePoints(), value.basisPoints(), value.annualizedBasisDecimal(), value.maturityDate(),
        value.daysLeft(), value.sourceName())).toList();
  }

  @Override
  public List<MarketOverview.MarketSourceEvent> recentSourceEvents(String marketSyncRunId, int limit) {
    return mapper.recentSourceEvents(marketSyncRunId, limit).stream().map(value -> new MarketOverview.MarketSourceEvent(
        value.marketSourceEventId(), value.instrumentId(), value.symbol(), value.sourceName(), value.eventType(),
        value.severity(), value.errorCode(), value.errorSummary(), value.createdAt())).toList();
  }

  private MarketOverview.MarketRun run(MarketOverviewMapper.MarketRunRow value) {
    return new MarketOverview.MarketRun(value.marketSyncRunId(), value.tradingDate(), value.runType(), value.status(),
        value.triggeredBy(), value.startedAt(), value.completedAt(), List.of());
  }

  private MarketOverview.MarketAttempt attempt(MarketOverviewMapper.MarketAttemptRow value) {
    return new MarketOverview.MarketAttempt(value.marketSyncAttemptId(), value.attemptNo(), value.triggerType(),
        value.status(), value.sourceName(), value.errorCode(), value.errorSummary(), value.startedAt(),
        value.completedAt());
  }
}
