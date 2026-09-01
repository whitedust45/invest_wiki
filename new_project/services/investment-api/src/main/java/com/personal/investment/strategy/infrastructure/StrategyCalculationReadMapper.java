package com.personal.investment.strategy.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Read-only strategy projection queries. Every ledger query ignores a fact only after its immutable mirror reversal
 * has been appended; no strategy calculation updates a projection or transaction row.
 */
@Mapper
public interface StrategyCalculationReadMapper {
  @Select("""
      SELECT COALESCE((SELECT ledger_version FROM ledger_db.ledger_state
                       WHERE owner_user_id = #{ownerUserId}), 0) AS ledgerVersion,
             COUNT(DISTINCT tx.transaction_id) AS transactionCount,
             COALESCE(MAX(CASE WHEN posting.currency <> #{currency} THEN 1 ELSE 0 END), 0) AS currencyMismatch,
             COALESCE(SUM(CASE
               WHEN account.account_kind = 'ASSET_CASH' AND posting.posting_side = 'DEBIT' THEN posting.amount_cent
               WHEN account.account_kind = 'ASSET_CASH' AND posting.posting_side = 'CREDIT' THEN -posting.amount_cent
               ELSE 0 END), 0) AS cashBalanceCent
      FROM ledger_db.ledger_transaction tx
      LEFT JOIN ledger_db.ledger_posting posting ON posting.transaction_id = tx.transaction_id
      LEFT JOIN ledger_db.ledger_account account ON account.account_id = posting.account_id
      WHERE tx.owner_user_id = #{ownerUserId} AND tx.strategy_key = #{strategyKey}
        AND tx.occurred_on <= #{asOfDate}
        AND NOT EXISTS (
          SELECT 1 FROM ledger_db.ledger_transaction reversal
          WHERE reversal.reversal_of_transaction_id = tx.transaction_id AND reversal.transaction_type = 'REVERSAL')
      """)
  LedgerSummaryRow findLedgerSummary(@Param("ownerUserId") String ownerUserId,
      @Param("strategyKey") String strategyKey, @Param("currency") String currency,
      @Param("asOfDate") LocalDate asOfDate);

  @Select("""
      SELECT COALESCE(SUM(income.gross_amount_cent - income.tax_withheld_cent), 0) AS trailingIncomeCent
      FROM ledger_db.ledger_income_detail income
      INNER JOIN ledger_db.ledger_transaction tx ON tx.transaction_id = income.transaction_id
      WHERE tx.owner_user_id = #{ownerUserId} AND tx.strategy_key = #{strategyKey}
        AND tx.occurred_on BETWEEN DATE_SUB(#{asOfDate}, INTERVAL 12 MONTH) AND #{asOfDate}
        AND NOT EXISTS (
          SELECT 1 FROM ledger_db.ledger_transaction reversal
          WHERE reversal.reversal_of_transaction_id = tx.transaction_id AND reversal.transaction_type = 'REVERSAL')
      """)
  long findTrailingNetIncome(@Param("ownerUserId") String ownerUserId, @Param("strategyKey") String strategyKey,
      @Param("asOfDate") LocalDate asOfDate);

  @Select("""
      SELECT COALESCE(SUM(CASE
               WHEN account.account_kind = 'ASSET_CASH' AND posting.posting_side = 'DEBIT' THEN posting.amount_cent
               WHEN account.account_kind = 'ASSET_CASH' AND posting.posting_side = 'CREDIT' THEN -posting.amount_cent
               ELSE 0 END), 0) AS cashCent,
             COALESCE(SUM(CASE
               WHEN account.account_code LIKE 'MRGAV:%' AND posting.posting_side = 'DEBIT' THEN posting.amount_cent
               WHEN account.account_code LIKE 'MRGAV:%' AND posting.posting_side = 'CREDIT' THEN -posting.amount_cent
               ELSE 0 END), 0) AS availableMarginCent,
             COALESCE(SUM(CASE
               WHEN account.account_code LIKE 'MRGLK:%' AND posting.posting_side = 'DEBIT' THEN posting.amount_cent
               WHEN account.account_code LIKE 'MRGLK:%' AND posting.posting_side = 'CREDIT' THEN -posting.amount_cent
               ELSE 0 END), 0) AS lockedMarginCent
      FROM ledger_db.ledger_transaction tx
      INNER JOIN ledger_db.ledger_posting posting ON posting.transaction_id = tx.transaction_id
      INNER JOIN ledger_db.ledger_account account ON account.account_id = posting.account_id
      WHERE tx.owner_user_id = #{ownerUserId} AND tx.strategy_key = #{strategyKey}
        AND tx.occurred_on <= #{asOfDate}
        AND NOT EXISTS (
          SELECT 1 FROM ledger_db.ledger_transaction reversal
          WHERE reversal.reversal_of_transaction_id = tx.transaction_id AND reversal.transaction_type = 'REVERSAL')
      """)
  MarginSummaryRow findMarginSummary(@Param("ownerUserId") String ownerUserId,
      @Param("strategyKey") String strategyKey, @Param("asOfDate") LocalDate asOfDate);

  @Select("""
      SELECT detail.instrument_id AS instrumentId, instrument.symbol,
             SUM(CASE WHEN tx.transaction_type = 'TRADE_BUY' THEN detail.quantity ELSE -detail.quantity END)
               AS netQuantity
      FROM ledger_db.ledger_transaction tx
      INNER JOIN ledger_db.ledger_trade_detail detail ON detail.transaction_id = tx.transaction_id
      INNER JOIN market_db.instrument instrument ON instrument.instrument_id = detail.instrument_id
      WHERE tx.owner_user_id = #{ownerUserId} AND tx.strategy_key = #{strategyKey}
        AND tx.transaction_type IN ('TRADE_BUY', 'TRADE_SELL')
        AND instrument.symbol IN ('QQQ', 'QLD') AND tx.occurred_on <= #{asOfDate}
        AND NOT EXISTS (
          SELECT 1 FROM ledger_db.ledger_transaction reversal
          WHERE reversal.reversal_of_transaction_id = tx.transaction_id AND reversal.transaction_type = 'REVERSAL')
      GROUP BY detail.instrument_id, instrument.symbol
      """)
  List<SpotPositionRow> findQqqPositions(@Param("ownerUserId") String ownerUserId,
      @Param("strategyKey") String strategyKey, @Param("asOfDate") LocalDate asOfDate);

  @Select("""
      SELECT market_sync_run_id AS marketSyncRunId, trading_date AS tradingDate, completed_at AS completedAt
      FROM market_db.market_sync_run
      WHERE status = 'SUCCEEDED' AND completed_at IS NOT NULL AND trading_date <= #{asOfDate}
      ORDER BY trading_date DESC, completed_at DESC, market_sync_run_id DESC
      LIMIT 1
      """)
  MarketSyncRunRow findLatestSucceededMarketRun(@Param("asOfDate") LocalDate asOfDate);

  @Select("""
      SELECT instrument.symbol, quote.quote_snapshot_id AS quoteSnapshotId, quote.price_cent AS priceCent,
             quote.currency
      FROM market_db.quote_snapshot quote
      INNER JOIN market_db.instrument instrument ON instrument.instrument_id = quote.instrument_id
      WHERE quote.market_sync_run_id = #{marketSyncRunId} AND instrument.symbol IN ('QQQ', 'QLD')
        AND NOT EXISTS (
          SELECT 1 FROM market_db.quote_snapshot newer
          WHERE newer.instrument_id = quote.instrument_id AND newer.market_sync_run_id = quote.market_sync_run_id
            AND (newer.quote_time > quote.quote_time
              OR (newer.quote_time = quote.quote_time AND newer.revision_no > quote.revision_no)
              OR (newer.quote_time = quote.quote_time AND newer.revision_no = quote.revision_no
                  AND newer.quote_snapshot_id > quote.quote_snapshot_id)))
      """)
  List<QuoteRow> findQqqQuotesForRun(@Param("marketSyncRunId") String marketSyncRunId);

  @Select("""
      SELECT DISTINCT contract.product_code AS productCode
      FROM ledger_db.ledger_transaction tx
      INNER JOIN ledger_db.ledger_trade_detail detail ON detail.transaction_id = tx.transaction_id
      INNER JOIN market_db.futures_contract contract ON contract.instrument_id = detail.instrument_id
      WHERE tx.owner_user_id = #{ownerUserId} AND tx.strategy_key = #{strategyKey}
        AND tx.transaction_type IN ('FUTURES_OPEN', 'FUTURES_CLOSE', 'FUTURES_DAILY_SETTLEMENT')
        AND tx.occurred_on <= #{asOfDate}
        AND NOT EXISTS (
          SELECT 1 FROM ledger_db.ledger_transaction reversal
          WHERE reversal.reversal_of_transaction_id = tx.transaction_id AND reversal.transaction_type = 'REVERSAL')
      """)
  List<String> findFuturesProducts(@Param("ownerUserId") String ownerUserId,
      @Param("strategyKey") String strategyKey, @Param("asOfDate") LocalDate asOfDate);

  @Select("""
      SELECT contract.product_code AS productCode, metric.daily_metric_id AS dailyMetricId,
             metric.metric_value_decimal AS metricValueDecimal
      FROM market_db.daily_metric metric
      INNER JOIN market_db.instrument underlying ON underlying.instrument_id = metric.instrument_id
      INNER JOIN market_db.instrument future ON future.underlying_instrument_id = underlying.instrument_id
      INNER JOIN market_db.futures_contract contract ON contract.instrument_id = future.instrument_id
      WHERE metric.market_sync_run_id = #{marketSyncRunId} AND metric.metric_name = 'PB_PERCENTILE'
        AND metric.metric_value_decimal IS NOT NULL AND contract.product_code IN ('IC', 'IM')
        AND NOT EXISTS (
          SELECT 1 FROM market_db.daily_metric newer
          INNER JOIN market_db.instrument newerUnderlying ON newerUnderlying.instrument_id = newer.instrument_id
          INNER JOIN market_db.instrument newerFuture
            ON newerFuture.underlying_instrument_id = newerUnderlying.instrument_id
          INNER JOIN market_db.futures_contract newerContract ON newerContract.instrument_id = newerFuture.instrument_id
          WHERE newer.market_sync_run_id = metric.market_sync_run_id AND newer.metric_name = metric.metric_name
            AND newerContract.product_code = contract.product_code AND newer.metric_value_decimal IS NOT NULL
            AND (newer.trade_date > metric.trade_date
              OR (newer.trade_date = metric.trade_date AND newer.revision_no > metric.revision_no)
              OR (newer.trade_date = metric.trade_date AND newer.revision_no = metric.revision_no
                  AND newer.daily_metric_id > metric.daily_metric_id)))
      """)
  List<PbMetricRow> findPbMetricsForRun(@Param("marketSyncRunId") String marketSyncRunId);

  @Select("""
      SELECT contract.product_code AS productCode, basis.basis_snapshot_id AS basisSnapshotId,
             basis.annualized_basis_decimal AS annualizedBasis
      FROM market_db.basis_snapshot basis
      INNER JOIN market_db.futures_contract contract ON contract.instrument_id = basis.future_instrument_id
      WHERE basis.market_sync_run_id = #{marketSyncRunId} AND contract.product_code IN ('IC', 'IM')
        AND basis.annualized_basis_decimal IS NOT NULL
        AND NOT EXISTS (
          SELECT 1 FROM market_db.basis_snapshot newer
          INNER JOIN market_db.futures_contract newerContract ON newerContract.instrument_id = newer.future_instrument_id
          WHERE newer.market_sync_run_id = basis.market_sync_run_id
            AND newerContract.product_code = contract.product_code AND newer.annualized_basis_decimal IS NOT NULL
            AND (newer.trade_date > basis.trade_date
              OR (newer.trade_date = basis.trade_date AND newer.revision_no > basis.revision_no)
              OR (newer.trade_date = basis.trade_date AND newer.revision_no = basis.revision_no
                  AND newer.basis_snapshot_id > basis.basis_snapshot_id)))
      """)
  List<BasisRow> findBasisForRun(@Param("marketSyncRunId") String marketSyncRunId);

  @Select("""
      SELECT contract.product_code AS productCode, instrument.maturity_date AS maturityDate,
             SUM(CASE WHEN tx.transaction_type = 'FUTURES_OPEN' THEN detail.quantity ELSE -detail.quantity END)
               AS netQuantity
      FROM ledger_db.ledger_transaction tx
      INNER JOIN ledger_db.ledger_trade_detail detail ON detail.transaction_id = tx.transaction_id
      INNER JOIN market_db.instrument instrument ON instrument.instrument_id = detail.instrument_id
      INNER JOIN market_db.futures_contract contract ON contract.instrument_id = detail.instrument_id
      WHERE tx.owner_user_id = #{ownerUserId} AND tx.strategy_key = #{strategyKey}
        AND tx.transaction_type IN ('FUTURES_OPEN', 'FUTURES_CLOSE') AND tx.occurred_on <= #{asOfDate}
        AND NOT EXISTS (
          SELECT 1 FROM ledger_db.ledger_transaction reversal
          WHERE reversal.reversal_of_transaction_id = tx.transaction_id AND reversal.transaction_type = 'REVERSAL')
      GROUP BY contract.product_code, instrument.maturity_date, detail.instrument_id
      """)
  List<FuturesPositionRow> findFuturesPositions(@Param("ownerUserId") String ownerUserId,
      @Param("strategyKey") String strategyKey, @Param("asOfDate") LocalDate asOfDate);

  @Select("""
      SELECT tx.transaction_type AS transactionType, tx.occurred_on AS occurredOn, detail.instrument_id AS instrumentId,
             detail.quantity, detail.unit_price_cent AS unitPriceCent, detail.fee_cent AS feeCent,
             detail.option_contract_multiplier AS optionContractMultiplier, instrument.maturity_date AS maturityDate,
             contract.option_right AS optionRight, contract.currency
      FROM ledger_db.ledger_transaction tx
      INNER JOIN ledger_db.ledger_trade_detail detail ON detail.transaction_id = tx.transaction_id
      INNER JOIN market_db.instrument instrument ON instrument.instrument_id = detail.instrument_id
      INNER JOIN market_db.option_contract contract ON contract.instrument_id = detail.instrument_id
      WHERE tx.owner_user_id = #{ownerUserId} AND tx.strategy_key = #{strategyKey}
        AND tx.transaction_type IN ('OPTION_OPEN', 'OPTION_CLOSE', 'OPTION_EXPIRE') AND tx.occurred_on <= #{asOfDate}
        AND NOT EXISTS (
          SELECT 1 FROM ledger_db.ledger_transaction reversal
          WHERE reversal.reversal_of_transaction_id = tx.transaction_id AND reversal.transaction_type = 'REVERSAL')
      ORDER BY tx.occurred_on, tx.transaction_id, detail.detail_no
      """)
  List<OptionTradeRow> findOptionTrades(@Param("ownerUserId") String ownerUserId,
      @Param("strategyKey") String strategyKey, @Param("asOfDate") LocalDate asOfDate);

  record LedgerSummaryRow(long ledgerVersion, long transactionCount, int currencyMismatch, long cashBalanceCent) {
  }

  record MarginSummaryRow(long cashCent, long availableMarginCent, long lockedMarginCent) {
  }

  record SpotPositionRow(String instrumentId, String symbol, BigDecimal netQuantity) {
  }

  record MarketSyncRunRow(String marketSyncRunId, LocalDate tradingDate, Instant completedAt) {
  }

  record QuoteRow(String symbol, String quoteSnapshotId, long priceCent, String currency) {
  }

  record PbMetricRow(String productCode, String dailyMetricId, BigDecimal metricValueDecimal) {
  }

  record BasisRow(String productCode, String basisSnapshotId, BigDecimal annualizedBasis) {
  }

  record FuturesPositionRow(String productCode, LocalDate maturityDate, BigDecimal netQuantity) {
  }

  record OptionTradeRow(String transactionType, LocalDate occurredOn, String instrumentId, BigDecimal quantity,
                        Long unitPriceCent, long feeCent, Long optionContractMultiplier, LocalDate maturityDate,
                        String optionRight, String currency) {
  }
}
