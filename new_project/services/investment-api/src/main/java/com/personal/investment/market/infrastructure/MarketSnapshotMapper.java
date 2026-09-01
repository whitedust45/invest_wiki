package com.personal.investment.market.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MarketSnapshotMapper {
  @Insert("""
      INSERT INTO market_db.market_sync_run
        (market_sync_run_id, job_id, run_type, trading_date, source_policy_version, status, triggered_by,
         started_at, completed_at, created_at)
      VALUES (#{marketSyncRunId}, NULL, #{runType}, #{tradingDate}, #{sourcePolicyVersion}, #{status},
              #{triggeredBy}, #{startedAt}, NULL, UTC_TIMESTAMP(3))
      """)
  int insertRun(@Param("marketSyncRunId") String marketSyncRunId, @Param("runType") String runType,
                @Param("tradingDate") LocalDate tradingDate, @Param("sourcePolicyVersion") String sourcePolicyVersion,
                @Param("status") String status, @Param("triggeredBy") String triggeredBy,
                @Param("startedAt") Instant startedAt);

  @Select("""
      SELECT market_sync_run_id AS marketSyncRunId, trading_date AS tradingDate, run_type AS runType, status,
             started_at AS startedAt, completed_at AS completedAt
      FROM market_db.market_sync_run WHERE market_sync_run_id = #{marketSyncRunId} LIMIT 1
      """)
  RunRow findRun(@Param("marketSyncRunId") String marketSyncRunId);

  @Select("""
      SELECT market_sync_run_id AS marketSyncRunId, trading_date AS tradingDate, run_type AS runType, status,
             started_at AS startedAt, completed_at AS completedAt
      FROM market_db.market_sync_run
      WHERE trading_date = #{tradingDate} AND status = 'SUCCEEDED'
      ORDER BY completed_at DESC, market_sync_run_id DESC LIMIT 1
      """)
  RunRow findSucceededRunForTradingDate(@Param("tradingDate") LocalDate tradingDate);

  @Insert("""
      INSERT INTO market_db.market_snapshot_submission
        (market_snapshot_submission_id, submitted_by_user_id, market_sync_run_id, trading_date, source_name,
         source_reference, status, applied_at, created_at, updated_at)
      VALUES (#{submissionId}, #{submittedByUserId}, #{marketSyncRunId}, #{tradingDate}, #{sourceName},
              #{sourceReference}, #{status}, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
      """)
  int insertSubmission(@Param("submissionId") String submissionId, @Param("submittedByUserId") String submittedByUserId,
                       @Param("marketSyncRunId") String marketSyncRunId, @Param("tradingDate") LocalDate tradingDate,
                       @Param("sourceName") String sourceName, @Param("sourceReference") String sourceReference,
                       @Param("status") String status);

  @Insert("""
      INSERT INTO market_db.market_snapshot_submission_quote
        (market_snapshot_submission_quote_id, market_snapshot_submission_id, instrument_id, quote_time,
         source_observation_key, price_cent, prev_close_cent, currency, created_at)
      VALUES (#{detailId}, #{submissionId}, #{instrumentId}, #{quoteTime}, #{sourceObservationKey}, #{priceCent},
              #{prevCloseCent}, #{currency}, UTC_TIMESTAMP(3))
      """)
  int insertSubmissionQuote(@Param("detailId") String detailId, @Param("submissionId") String submissionId,
                            @Param("instrumentId") String instrumentId, @Param("quoteTime") Instant quoteTime,
                            @Param("sourceObservationKey") String sourceObservationKey, @Param("priceCent") long priceCent,
                            @Param("prevCloseCent") Long prevCloseCent, @Param("currency") String currency);

  @Insert("""
      INSERT INTO market_db.market_snapshot_submission_metric
        (market_snapshot_submission_metric_id, market_snapshot_submission_id, instrument_id, metric_name,
         metric_value_decimal, source_observation_key, created_at)
      VALUES (#{detailId}, #{submissionId}, #{instrumentId}, #{metricName}, #{metricValueDecimal},
              #{sourceObservationKey}, UTC_TIMESTAMP(3))
      """)
  int insertSubmissionMetric(@Param("detailId") String detailId, @Param("submissionId") String submissionId,
                             @Param("instrumentId") String instrumentId, @Param("metricName") String metricName,
                             @Param("metricValueDecimal") BigDecimal metricValueDecimal,
                             @Param("sourceObservationKey") String sourceObservationKey);

  @Insert("""
      INSERT INTO market_db.market_snapshot_submission_basis
        (market_snapshot_submission_basis_id, market_snapshot_submission_id, underlying_instrument_id,
         future_instrument_id, spot_price_points, future_price_points, annualized_basis_decimal, maturity_date,
         days_left, source_observation_key, created_at)
      VALUES (#{detailId}, #{submissionId}, #{underlyingInstrumentId}, #{futureInstrumentId}, #{spotPricePoints},
              #{futurePricePoints}, #{annualizedBasisDecimal}, #{maturityDate}, #{daysLeft},
              #{sourceObservationKey}, UTC_TIMESTAMP(3))
      """)
  int insertSubmissionBasis(@Param("detailId") String detailId, @Param("submissionId") String submissionId,
                            @Param("underlyingInstrumentId") String underlyingInstrumentId,
                            @Param("futureInstrumentId") String futureInstrumentId,
                            @Param("spotPricePoints") BigDecimal spotPricePoints,
                            @Param("futurePricePoints") BigDecimal futurePricePoints,
                            @Param("annualizedBasisDecimal") BigDecimal annualizedBasisDecimal,
                            @Param("maturityDate") LocalDate maturityDate, @Param("daysLeft") Integer daysLeft,
                            @Param("sourceObservationKey") String sourceObservationKey);

  @Select("""
      SELECT market_snapshot_submission_id AS marketSnapshotSubmissionId
      FROM market_db.market_snapshot_submission WHERE status = 'QUEUED' ORDER BY created_at LIMIT #{limit}
      """)
  List<String> findQueuedSubmissionIds(@Param("limit") int limit);

  @Update("""
      UPDATE market_db.market_snapshot_submission SET status = 'RUNNING', updated_at = #{claimedAt}
      WHERE market_snapshot_submission_id = #{submissionId} AND status = 'QUEUED'
      """)
  int claimSubmission(@Param("submissionId") String submissionId, @Param("claimedAt") Instant claimedAt);

  @Update("""
      UPDATE market_db.market_sync_run SET status = 'RUNNING'
      WHERE market_sync_run_id = #{marketSyncRunId} AND status = 'QUEUED'
      """)
  int markRunRunning(@Param("marketSyncRunId") String marketSyncRunId);

  @Select("""
      SELECT market_snapshot_submission_id AS marketSnapshotSubmissionId, submitted_by_user_id AS submittedByUserId,
             market_sync_run_id AS marketSyncRunId, trading_date AS tradingDate, source_name AS sourceName,
             source_reference AS sourceReference, status
      FROM market_db.market_snapshot_submission WHERE market_snapshot_submission_id = #{submissionId} LIMIT 1
      """)
  SubmissionRow findSubmission(@Param("submissionId") String submissionId);

  @Select("""
      SELECT instrument_id AS instrumentId, quote_time AS quoteTime, source_observation_key AS sourceObservationKey,
             price_cent AS priceCent, prev_close_cent AS prevCloseCent, currency
      FROM market_db.market_snapshot_submission_quote WHERE market_snapshot_submission_id = #{submissionId}
      ORDER BY id
      """)
  List<SubmissionQuoteRow> findSubmissionQuotes(@Param("submissionId") String submissionId);

  @Select("""
      SELECT instrument_id AS instrumentId, metric_name AS metricName, metric_value_decimal AS metricValueDecimal,
             source_observation_key AS sourceObservationKey
      FROM market_db.market_snapshot_submission_metric WHERE market_snapshot_submission_id = #{submissionId}
      ORDER BY id
      """)
  List<SubmissionMetricRow> findSubmissionMetrics(@Param("submissionId") String submissionId);

  @Select("""
      SELECT underlying_instrument_id AS underlyingInstrumentId, future_instrument_id AS futureInstrumentId,
             spot_price_points AS spotPricePoints, future_price_points AS futurePricePoints,
             annualized_basis_decimal AS annualizedBasisDecimal, maturity_date AS maturityDate, days_left AS daysLeft,
             source_observation_key AS sourceObservationKey
      FROM market_db.market_snapshot_submission_basis WHERE market_snapshot_submission_id = #{submissionId}
      ORDER BY id
      """)
  List<SubmissionBasisRow> findSubmissionBasis(@Param("submissionId") String submissionId);

  @Update("""
      UPDATE market_db.market_sync_run SET status = #{status}, completed_at = #{completedAt}
      WHERE market_sync_run_id = #{marketSyncRunId} AND status IN ('QUEUED','RUNNING')
      """)
  int markRunCompleted(@Param("marketSyncRunId") String marketSyncRunId, @Param("status") String status,
                       @Param("completedAt") Instant completedAt);

  @Update("""
      UPDATE market_db.market_snapshot_submission
      SET status = #{status}, applied_at = #{completedAt}, updated_at = #{completedAt}
      WHERE market_snapshot_submission_id = #{submissionId} AND status = 'RUNNING'
      """)
  int markSubmissionCompleted(@Param("submissionId") String submissionId, @Param("status") String status,
                              @Param("completedAt") Instant completedAt);

  @Insert("""
      INSERT INTO market_db.market_sync_attempt
        (market_sync_attempt_id, market_sync_run_id, attempt_no, trigger_type, status, source_name, error_code,
         error_summary, started_at, completed_at, created_at)
      VALUES (#{attemptId}, #{marketSyncRunId}, #{attemptNo}, #{triggerType}, #{status}, #{sourceName},
              #{errorCode}, #{errorSummary}, #{startedAt}, #{completedAt}, UTC_TIMESTAMP(3))
      """)
  int insertAttempt(@Param("attemptId") String attemptId, @Param("marketSyncRunId") String marketSyncRunId,
                    @Param("attemptNo") int attemptNo, @Param("triggerType") String triggerType,
                    @Param("status") String status, @Param("sourceName") String sourceName,
                    @Param("errorCode") String errorCode, @Param("errorSummary") String errorSummary,
                    @Param("startedAt") Instant startedAt, @Param("completedAt") Instant completedAt);

  @Insert("""
      INSERT INTO market_db.market_source_event
        (market_source_event_id, market_sync_run_id, instrument_id, source_name, event_type, severity, error_code,
         error_summary, import_export_file_id, created_at)
      VALUES (#{eventId}, #{marketSyncRunId}, #{instrumentId}, #{sourceName}, #{eventType}, #{severity},
              #{errorCode}, #{errorSummary}, NULL, UTC_TIMESTAMP(3))
      """)
  int insertSourceEvent(@Param("eventId") String eventId, @Param("marketSyncRunId") String marketSyncRunId,
                        @Param("instrumentId") String instrumentId, @Param("sourceName") String sourceName,
                        @Param("eventType") String eventType, @Param("severity") String severity,
                        @Param("errorCode") String errorCode, @Param("errorSummary") String errorSummary);

  @Insert("""
      INSERT INTO market_db.quote_snapshot
        (quote_snapshot_id, instrument_id, market_sync_run_id, quote_time, source_name, source_observation_key,
         revision_no, supersedes_quote_snapshot_id, observation_hash, price_cent, prev_close_cent, currency,
         import_export_file_id, created_at)
      VALUES (#{quoteSnapshotId}, #{instrumentId}, #{marketSyncRunId}, #{quoteTime}, #{sourceName},
              #{sourceObservationKey}, 0, NULL, #{observationHash}, #{priceCent}, #{prevCloseCent}, #{currency},
              NULL, UTC_TIMESTAMP(3))
      """)
  int insertQuote(@Param("quoteSnapshotId") String quoteSnapshotId, @Param("instrumentId") String instrumentId,
                  @Param("marketSyncRunId") String marketSyncRunId, @Param("quoteTime") Instant quoteTime,
                  @Param("sourceName") String sourceName, @Param("sourceObservationKey") String sourceObservationKey,
                  @Param("observationHash") byte[] observationHash, @Param("priceCent") long priceCent,
                  @Param("prevCloseCent") Long prevCloseCent, @Param("currency") String currency);

  @Insert("""
      INSERT INTO market_db.daily_metric
        (daily_metric_id, instrument_id, market_sync_run_id, trade_date, metric_name, source_name, revision_no,
         metric_value_decimal, metric_value_cent, currency, supersedes_daily_metric_id, observation_hash, created_at)
      VALUES (#{dailyMetricId}, #{instrumentId}, #{marketSyncRunId}, #{tradeDate}, #{metricName}, #{sourceName}, 0,
              #{valueDecimal}, NULL, NULL, NULL, #{observationHash}, UTC_TIMESTAMP(3))
      """)
  int insertMetric(@Param("dailyMetricId") String dailyMetricId, @Param("instrumentId") String instrumentId,
                   @Param("marketSyncRunId") String marketSyncRunId, @Param("tradeDate") LocalDate tradeDate,
                   @Param("metricName") String metricName, @Param("sourceName") String sourceName,
                   @Param("valueDecimal") BigDecimal valueDecimal, @Param("observationHash") byte[] observationHash);

  @Insert("""
      INSERT INTO market_db.basis_snapshot
        (basis_snapshot_id, underlying_instrument_id, future_instrument_id, market_sync_run_id, trade_date,
         source_name, revision_no, spot_price_points, future_price_points, basis_points, annualized_basis_decimal,
         maturity_date, days_left, supersedes_basis_snapshot_id, observation_hash, created_at)
      VALUES (#{basisSnapshotId}, #{underlyingInstrumentId}, #{futureInstrumentId}, #{marketSyncRunId}, #{tradeDate},
              #{sourceName}, 0, #{spotPricePoints}, #{futurePricePoints}, #{basisPoints},
              #{annualizedBasisDecimal}, #{maturityDate}, #{daysLeft}, NULL, #{observationHash}, UTC_TIMESTAMP(3))
      """)
  int insertBasis(@Param("basisSnapshotId") String basisSnapshotId,
                  @Param("underlyingInstrumentId") String underlyingInstrumentId,
                  @Param("futureInstrumentId") String futureInstrumentId,
                  @Param("marketSyncRunId") String marketSyncRunId, @Param("tradeDate") LocalDate tradeDate,
                  @Param("sourceName") String sourceName, @Param("spotPricePoints") BigDecimal spotPricePoints,
                  @Param("futurePricePoints") BigDecimal futurePricePoints, @Param("basisPoints") BigDecimal basisPoints,
                  @Param("annualizedBasisDecimal") BigDecimal annualizedBasisDecimal,
                  @Param("maturityDate") LocalDate maturityDate, @Param("daysLeft") Integer daysLeft,
                  @Param("observationHash") byte[] observationHash);

  @Select("""
      SELECT metric_value_decimal FROM market_db.daily_metric
      WHERE instrument_id = #{instrumentId} AND metric_name = #{metricName}
        AND trade_date BETWEEN #{startDate} AND #{endDate} AND metric_value_decimal IS NOT NULL
      ORDER BY metric_value_decimal
      """)
  List<BigDecimal> findMetricHistory(@Param("instrumentId") String instrumentId, @Param("metricName") String metricName,
                                     @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

  record RunRow(String marketSyncRunId, LocalDate tradingDate, String runType, String status, Instant startedAt,
                Instant completedAt) {
  }

  record SubmissionRow(String marketSnapshotSubmissionId, String submittedByUserId, String marketSyncRunId,
                       LocalDate tradingDate, String sourceName, String sourceReference, String status) {
  }

  record SubmissionQuoteRow(String instrumentId, Instant quoteTime, String sourceObservationKey, long priceCent,
                            Long prevCloseCent, String currency) {
  }

  record SubmissionMetricRow(String instrumentId, String metricName, BigDecimal metricValueDecimal,
                             String sourceObservationKey) {
  }

  record SubmissionBasisRow(String underlyingInstrumentId, String futureInstrumentId, BigDecimal spotPricePoints,
                            BigDecimal futurePricePoints, BigDecimal annualizedBasisDecimal, LocalDate maturityDate,
                            Integer daysLeft, String sourceObservationKey) {
  }
}
