package com.personal.investment.market.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MarketOverviewMapper {
  @Select("""
      SELECT market_sync_run_id AS marketSyncRunId, trading_date AS tradingDate, run_type AS runType, status,
             triggered_by AS triggeredBy, started_at AS startedAt, completed_at AS completedAt
      FROM market_db.market_sync_run
      ORDER BY trading_date DESC, created_at DESC, market_sync_run_id DESC
      LIMIT 1
      """)
  MarketRunRow latestRun();

  @Select("""
      SELECT market_sync_attempt_id AS marketSyncAttemptId, attempt_no AS attemptNo, trigger_type AS triggerType,
             status, source_name AS sourceName, error_code AS errorCode, error_summary AS errorSummary,
             started_at AS startedAt, completed_at AS completedAt
      FROM market_db.market_sync_attempt
      WHERE market_sync_run_id = #{marketSyncRunId}
      ORDER BY attempt_no ASC
      """)
  List<MarketAttemptRow> attempts(@Param("marketSyncRunId") String marketSyncRunId);

  @Select("""
      SELECT quote.quote_snapshot_id AS quoteSnapshotId, quote.instrument_id AS instrumentId, instrument.symbol,
             instrument.display_name AS displayName, quote.currency, quote.price_cent AS priceCent,
             quote.prev_close_cent AS prevCloseCent, quote.quote_time AS quoteTime, quote.source_name AS sourceName
      FROM market_db.quote_snapshot quote
      INNER JOIN market_db.instrument instrument ON instrument.instrument_id = quote.instrument_id
      WHERE quote.market_sync_run_id = #{marketSyncRunId}
        AND NOT EXISTS (
          SELECT 1 FROM market_db.quote_snapshot newer
          WHERE newer.market_sync_run_id = quote.market_sync_run_id AND newer.instrument_id = quote.instrument_id
            AND (newer.quote_time > quote.quote_time
              OR (newer.quote_time = quote.quote_time AND newer.revision_no > quote.revision_no)
              OR (newer.quote_time = quote.quote_time AND newer.revision_no = quote.revision_no
                  AND newer.quote_snapshot_id > quote.quote_snapshot_id))
        )
      ORDER BY instrument.symbol, quote.quote_time DESC
      """)
  List<MarketQuoteRow> currentQuotes(@Param("marketSyncRunId") String marketSyncRunId);

  @Select("""
      SELECT metric.daily_metric_id AS dailyMetricId, metric.instrument_id AS instrumentId, instrument.symbol,
             instrument.display_name AS displayName, metric.trade_date AS tradeDate, metric.metric_name AS metricName,
             metric.metric_value_decimal AS valueDecimal, metric.metric_value_cent AS valueCent, metric.currency,
             metric.source_name AS sourceName
      FROM market_db.daily_metric metric
      INNER JOIN market_db.instrument instrument ON instrument.instrument_id = metric.instrument_id
      WHERE metric.market_sync_run_id = #{marketSyncRunId}
        AND NOT EXISTS (
          SELECT 1 FROM market_db.daily_metric newer
          WHERE newer.market_sync_run_id = metric.market_sync_run_id AND newer.instrument_id = metric.instrument_id
            AND newer.metric_name = metric.metric_name
            AND (newer.trade_date > metric.trade_date
              OR (newer.trade_date = metric.trade_date AND newer.revision_no > metric.revision_no)
              OR (newer.trade_date = metric.trade_date AND newer.revision_no = metric.revision_no
                  AND newer.daily_metric_id > metric.daily_metric_id))
        )
      ORDER BY instrument.symbol, metric.metric_name
      """)
  List<MarketMetricRow> currentMetrics(@Param("marketSyncRunId") String marketSyncRunId);

  @Select("""
      SELECT basis.basis_snapshot_id AS basisSnapshotId, basis.underlying_instrument_id AS underlyingInstrumentId,
             underlying.symbol AS underlyingSymbol, basis.future_instrument_id AS futureInstrumentId,
             future.symbol AS futureSymbol, contract.product_code AS productCode, basis.trade_date AS tradeDate,
             basis.spot_price_points AS spotPricePoints, basis.future_price_points AS futurePricePoints,
             basis.basis_points AS basisPoints, basis.annualized_basis_decimal AS annualizedBasisDecimal,
             basis.maturity_date AS maturityDate, basis.days_left AS daysLeft, basis.source_name AS sourceName
      FROM market_db.basis_snapshot basis
      INNER JOIN market_db.instrument underlying ON underlying.instrument_id = basis.underlying_instrument_id
      INNER JOIN market_db.instrument future ON future.instrument_id = basis.future_instrument_id
      INNER JOIN market_db.futures_contract contract ON contract.instrument_id = basis.future_instrument_id
      WHERE basis.market_sync_run_id = #{marketSyncRunId}
        AND NOT EXISTS (
          SELECT 1 FROM market_db.basis_snapshot newer
          WHERE newer.market_sync_run_id = basis.market_sync_run_id
            AND newer.future_instrument_id = basis.future_instrument_id
            AND (newer.trade_date > basis.trade_date
              OR (newer.trade_date = basis.trade_date AND newer.revision_no > basis.revision_no)
              OR (newer.trade_date = basis.trade_date AND newer.revision_no = basis.revision_no
                  AND newer.basis_snapshot_id > basis.basis_snapshot_id))
        )
      ORDER BY contract.product_code, future.symbol
      """)
  List<MarketBasisRow> currentBasis(@Param("marketSyncRunId") String marketSyncRunId);

  @Select("""
      SELECT event.market_source_event_id AS marketSourceEventId, event.instrument_id AS instrumentId,
             instrument.symbol, event.source_name AS sourceName, event.event_type AS eventType, event.severity,
             event.error_code AS errorCode, event.error_summary AS errorSummary, event.created_at AS createdAt
      FROM market_db.market_source_event event
      LEFT JOIN market_db.instrument instrument ON instrument.instrument_id = event.instrument_id
      WHERE event.market_sync_run_id = #{marketSyncRunId}
      ORDER BY event.created_at DESC, event.market_source_event_id DESC
      LIMIT #{limit}
      """)
  List<MarketSourceEventRow> recentSourceEvents(@Param("marketSyncRunId") String marketSyncRunId,
                                                @Param("limit") int limit);

  record MarketRunRow(String marketSyncRunId, LocalDate tradingDate, String runType, String status,
                      String triggeredBy, Instant startedAt, Instant completedAt) {
  }
  record MarketAttemptRow(String marketSyncAttemptId, int attemptNo, String triggerType, String status,
                          String sourceName, String errorCode, String errorSummary, Instant startedAt,
                          Instant completedAt) {
  }
  record MarketQuoteRow(String quoteSnapshotId, String instrumentId, String symbol, String displayName,
                        String currency, long priceCent, Long prevCloseCent, Instant quoteTime, String sourceName) {
  }
  record MarketMetricRow(String dailyMetricId, String instrumentId, String symbol, String displayName,
                         LocalDate tradeDate, String metricName, BigDecimal valueDecimal, Long valueCent,
                         String currency, String sourceName) {
  }
  record MarketBasisRow(String basisSnapshotId, String underlyingInstrumentId, String underlyingSymbol,
                        String futureInstrumentId, String futureSymbol, String productCode, LocalDate tradeDate,
                        BigDecimal spotPricePoints, BigDecimal futurePricePoints, BigDecimal basisPoints,
                        BigDecimal annualizedBasisDecimal, LocalDate maturityDate, Integer daysLeft, String sourceName) {
  }
  record MarketSourceEventRow(String marketSourceEventId, String instrumentId, String symbol, String sourceName,
                              String eventType, String severity, String errorCode, String errorSummary,
                              Instant createdAt) {
  }
}
