-- 初始正式 schema：该文件是唯一可执行的 V1 迁移真源。
-- 所有自研实体直接使用语义化 ULID 业务主键；禁止泛化业务主键、物理外键及泛化关系 ID。
-- 所有货币金额按原币种两位小数最小单位存为 *_cent BIGINT：CNY 6.66 / USD 6.66 均保存为 666。

CREATE SCHEMA IF NOT EXISTS identity_db;
CREATE SCHEMA IF NOT EXISTS ledger_db;
CREATE SCHEMA IF NOT EXISTS portfolio_db;
CREATE SCHEMA IF NOT EXISTS market_db;
CREATE SCHEMA IF NOT EXISTS strategy_db;
CREATE SCHEMA IF NOT EXISTS reporting_db;
CREATE SCHEMA IF NOT EXISTS platform_db;

CREATE TABLE identity_db.iam_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(16) NOT NULL,
  permission_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  UNIQUE KEY uk_iam_user_user_id (user_id),
  CHECK (status IN ('ACTIVE','DENIED'))
);
CREATE TABLE identity_db.iam_wechat_identity (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  wechat_identity_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  openid_hmac BINARY(32) NOT NULL,
  hmac_key_version SMALLINT UNSIGNED NOT NULL,
  openid_ciphertext VARBINARY(512) NULL,
  encryption_key_version SMALLINT UNSIGNED NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_iam_wechat_identity_wechat_identity_id (wechat_identity_id),
  UNIQUE KEY uk_wechat_identity_hmac (openid_hmac),
  KEY idx_wechat_identity_user (user_id)
);
CREATE TABLE identity_db.iam_login_audit (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  login_audit_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  wechat_identity_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  login_result VARCHAR(32) NOT NULL,
  ip_hash BINARY(32) NULL,
  trace_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  failure_code VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_iam_login_audit_login_audit_id (login_audit_id),
  KEY idx_login_audit_user_time (user_id, created_at DESC)
);

CREATE TABLE ledger_db.ledger_account (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  account_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  account_code VARCHAR(64) NOT NULL,
  account_kind VARCHAR(32) NOT NULL,
  currency CHAR(3) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ledger_account_account_id (account_id),
  UNIQUE KEY uk_ledger_account_owner_code (owner_user_id, account_code),
  KEY idx_ledger_account_owner_kind (owner_user_id, account_kind, currency),
  CHECK (account_kind IN ('ASSET_CASH','ASSET_MARGIN','ASSET_INVESTMENT','ASSET_CLEARING','EQUITY_EXTERNAL','INCOME_DIVIDEND','INCOME_INTEREST','EXPENSE_FEE','EXPENSE_OPTION','PNL_REALIZED'))
);
CREATE TABLE ledger_db.ledger_transaction (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  transaction_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  transaction_type VARCHAR(32) NOT NULL,
  operation_group_key CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  occurred_on DATE NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  import_export_file_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  correction_root_transaction_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reversal_of_transaction_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  revision_no SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  note VARCHAR(1000) NULL,
  created_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ledger_transaction_transaction_id (transaction_id),
  UNIQUE KEY uk_ledger_tx_revision (correction_root_transaction_id, revision_no),
  KEY idx_ledger_tx_owner_date (owner_user_id, occurred_on DESC),
  KEY idx_ledger_tx_operation_group (operation_group_key),
  CHECK (reversal_of_transaction_id IS NULL OR reversal_of_transaction_id <> transaction_id)
);
CREATE TABLE ledger_db.ledger_posting (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  posting_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  transaction_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  account_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  posting_no SMALLINT UNSIGNED NOT NULL,
  posting_side VARCHAR(6) NOT NULL,
  amount_cent BIGINT NOT NULL,
  currency CHAR(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ledger_posting_posting_id (posting_id),
  UNIQUE KEY uk_ledger_posting_no (transaction_id, posting_no),
  KEY idx_ledger_posting_account (account_id, created_at DESC),
  CHECK (posting_side IN ('DEBIT','CREDIT')),
  CHECK (amount_cent > 0)
);
CREATE TABLE ledger_db.ledger_trade_detail (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  trade_detail_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  transaction_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  detail_no SMALLINT UNSIGNED NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  position_effect VARCHAR(8) NOT NULL,
  quantity DECIMAL(24,8) NOT NULL,
  unit_price_cent BIGINT NULL,
  price_points DECIMAL(24,8) NULL,
  contract_multiplier_cent BIGINT NULL,
  delivery_date DATE NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ledger_trade_detail_trade_detail_id (trade_detail_id),
  UNIQUE KEY uk_trade_detail_no (transaction_id, detail_no),
  KEY idx_trade_detail_instrument (instrument_id, created_at DESC),
  CHECK (position_effect IN ('OPEN','CLOSE','NONE')),
  CHECK (quantity > 0),
  CHECK ((unit_price_cent IS NOT NULL) <> (price_points IS NOT NULL)),
  CHECK (unit_price_cent IS NULL OR unit_price_cent > 0),
  CHECK (contract_multiplier_cent IS NULL OR contract_multiplier_cent > 0)
);
CREATE TABLE ledger_db.ledger_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  ledger_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  as_of_date DATE NOT NULL,
  source_ledger_version BIGINT UNSIGNED NOT NULL,
  import_export_file_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  checksum BINARY(32) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ledger_snapshot_ledger_snapshot_id (ledger_snapshot_id),
  UNIQUE KEY uk_ledger_snapshot (owner_user_id, as_of_date, source_ledger_version)
);

CREATE TABLE portfolio_db.portfolio_position (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  position_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  account_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  quantity DECIMAL(24,8) NOT NULL,
  average_cost_cent BIGINT NULL,
  currency CHAR(3) NOT NULL,
  source_ledger_version BIGINT UNSIGNED NOT NULL,
  projection_version BIGINT UNSIGNED NOT NULL,
  calculated_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_portfolio_position_position_id (position_id),
  UNIQUE KEY uk_position (owner_user_id, account_id, instrument_id),
  KEY idx_position_owner_currency (owner_user_id, currency),
  CHECK (average_cost_cent IS NULL OR average_cost_cent >= 0)
);
CREATE TABLE portfolio_db.portfolio_position_lot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  position_lot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  position_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_trade_detail_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  lot_no SMALLINT UNSIGNED NOT NULL,
  opened_on DATE NOT NULL,
  opened_quantity DECIMAL(24,8) NOT NULL,
  remaining_quantity DECIMAL(24,8) NOT NULL,
  unit_cost_cent BIGINT NOT NULL,
  currency CHAR(3) NOT NULL,
  source_ledger_version BIGINT UNSIGNED NOT NULL,
  calculated_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_portfolio_position_lot_position_lot_id (position_lot_id),
  UNIQUE KEY uk_position_lot (position_id, lot_no),
  UNIQUE KEY uk_position_lot_source (source_trade_detail_id),
  CHECK (opened_quantity > 0),
  CHECK (remaining_quantity >= 0 AND remaining_quantity <= opened_quantity),
  CHECK (unit_cost_cent >= 0)
);
CREATE TABLE portfolio_db.portfolio_manual_valuation (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  manual_valuation_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  valuation_date DATE NOT NULL,
  market_value_cent BIGINT NULL,
  unit_price_cent BIGINT NULL,
  currency CHAR(3) NOT NULL,
  priority SMALLINT UNSIGNED NOT NULL,
  valid_until DATETIME(3) NULL,
  note VARCHAR(1000) NULL,
  created_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_portfolio_manual_valuation_manual_valuation_id (manual_valuation_id),
  KEY idx_manual_valuation (owner_user_id, instrument_id, valuation_date DESC),
  CHECK (market_value_cent IS NOT NULL OR unit_price_cent IS NOT NULL),
  CHECK (market_value_cent IS NULL OR market_value_cent >= 0),
  CHECK (unit_price_cent IS NULL OR unit_price_cent >= 0)
);
CREATE TABLE portfolio_db.portfolio_daily_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  daily_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  currency CHAR(3) NOT NULL,
  as_of_date DATE NOT NULL,
  net_asset_cent BIGINT NOT NULL,
  cash_cent BIGINT NOT NULL,
  market_value_cent BIGINT NOT NULL,
  source_ledger_version BIGINT UNSIGNED NOT NULL,
  projection_version BIGINT UNSIGNED NOT NULL,
  calculated_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_portfolio_daily_snapshot_daily_snapshot_id (daily_snapshot_id),
  UNIQUE KEY uk_portfolio_daily_snapshot (owner_user_id, currency, as_of_date, source_ledger_version)
);

CREATE TABLE market_db.instrument (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market VARCHAR(32) NOT NULL,
  exchange VARCHAR(32) NOT NULL,
  symbol VARCHAR(64) NOT NULL,
  ts_code VARCHAR(64) NULL,
  asset_type VARCHAR(32) NOT NULL,
  display_name VARCHAR(256) NOT NULL,
  native_currency CHAR(3) NOT NULL,
  underlying_instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  maturity_date DATE NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_instrument_instrument_id (instrument_id),
  UNIQUE KEY uk_instrument_symbol (market, exchange, symbol),
  UNIQUE KEY uk_instrument_ts_code (ts_code),
  KEY idx_instrument_underlying (underlying_instrument_id)
);
CREATE TABLE market_db.instrument_alias (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  instrument_alias_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_name VARCHAR(64) NOT NULL,
  alias_code VARCHAR(128) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_instrument_alias_instrument_alias_id (instrument_alias_id),
  UNIQUE KEY uk_instrument_alias (source_name, alias_code),
  KEY idx_alias_instrument (instrument_id)
);
CREATE TABLE market_db.watchlist (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  watchlist_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  UNIQUE KEY uk_watchlist_watchlist_id (watchlist_id),
  UNIQUE KEY uk_watchlist_owner_name (owner_user_id, display_name)
);
CREATE TABLE market_db.watchlist_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  watchlist_item_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  watchlist_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  sort_order SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_watchlist_item_watchlist_item_id (watchlist_item_id),
  UNIQUE KEY uk_watchlist_item (watchlist_id, instrument_id)
);
CREATE TABLE market_db.market_sync_run (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  market_sync_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  job_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  run_type VARCHAR(64) NOT NULL,
  trading_date DATE NOT NULL,
  source_policy_version VARCHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  triggered_by VARCHAR(32) NOT NULL,
  started_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_market_sync_run_market_sync_run_id (market_sync_run_id),
  UNIQUE KEY uk_market_sync_run (run_type, trading_date, source_policy_version),
  KEY idx_market_sync_job (job_id)
);
CREATE TABLE market_db.market_sync_attempt (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  market_sync_attempt_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market_sync_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  attempt_no SMALLINT UNSIGNED NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  status VARCHAR(24) NOT NULL,
  source_name VARCHAR(64) NULL,
  error_code VARCHAR(64) NULL,
  error_summary VARCHAR(512) NULL,
  started_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_market_sync_attempt_market_sync_attempt_id (market_sync_attempt_id),
  UNIQUE KEY uk_market_sync_attempt (market_sync_run_id, attempt_no),
  KEY idx_market_attempt_status (status, started_at)
);
CREATE TABLE market_db.quote_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  quote_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market_sync_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  quote_time DATETIME(3) NOT NULL,
  source_name VARCHAR(64) NOT NULL,
  source_observation_key VARCHAR(256) NOT NULL,
  revision_no SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  supersedes_quote_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  observation_hash BINARY(32) NOT NULL,
  price_cent BIGINT NOT NULL,
  prev_close_cent BIGINT NULL,
  currency CHAR(3) NOT NULL,
  import_export_file_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_quote_snapshot_quote_snapshot_id (quote_snapshot_id),
  UNIQUE KEY uk_quote_revision (instrument_id, quote_time, source_name, source_observation_key, revision_no),
  UNIQUE KEY uk_quote_observation_hash (instrument_id, quote_time, source_name, observation_hash),
  KEY idx_quote_current_lookup (instrument_id, quote_time DESC, source_name, revision_no DESC),
  CHECK (price_cent > 0),
  CHECK (prev_close_cent IS NULL OR prev_close_cent > 0)
);
CREATE TABLE market_db.daily_bar (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  daily_bar_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market_sync_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  trade_date DATE NOT NULL,
  adjustment VARCHAR(16) NOT NULL,
  source_name VARCHAR(64) NOT NULL,
  revision_no SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  supersedes_daily_bar_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  observation_hash BINARY(32) NOT NULL,
  open_cent BIGINT NULL,
  high_cent BIGINT NULL,
  low_cent BIGINT NULL,
  close_cent BIGINT NOT NULL,
  turnover_cent BIGINT NULL,
  volume DECIMAL(30,8) NULL,
  currency CHAR(3) NOT NULL,
  import_export_file_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_daily_bar_daily_bar_id (daily_bar_id),
  UNIQUE KEY uk_daily_bar_revision (instrument_id, trade_date, adjustment, source_name, revision_no),
  UNIQUE KEY uk_daily_bar_hash (instrument_id, trade_date, adjustment, source_name, observation_hash),
  KEY idx_daily_bar_lookup (instrument_id, trade_date DESC, adjustment, source_name, revision_no DESC),
  CHECK (close_cent > 0)
);
CREATE TABLE market_db.daily_metric (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  daily_metric_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market_sync_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  trade_date DATE NOT NULL,
  metric_name VARCHAR(64) NOT NULL,
  source_name VARCHAR(64) NOT NULL,
  revision_no SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  metric_value_decimal DECIMAL(30,12) NULL,
  metric_value_cent BIGINT NULL,
  currency CHAR(3) NULL,
  supersedes_daily_metric_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  observation_hash BINARY(32) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_daily_metric_daily_metric_id (daily_metric_id),
  UNIQUE KEY uk_daily_metric_revision (instrument_id, trade_date, metric_name, source_name, revision_no),
  UNIQUE KEY uk_daily_metric_hash (instrument_id, trade_date, metric_name, source_name, observation_hash),
  CHECK ((metric_value_decimal IS NOT NULL AND metric_value_cent IS NULL) OR (metric_value_decimal IS NULL AND metric_value_cent IS NOT NULL AND currency IS NOT NULL))
);
CREATE TABLE market_db.adjustment_factor (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  adjustment_factor_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market_sync_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  trade_date DATE NOT NULL,
  source_name VARCHAR(64) NOT NULL,
  revision_no SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  factor_decimal DECIMAL(30,12) NOT NULL,
  supersedes_adjustment_factor_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  observation_hash BINARY(32) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_adjustment_factor_adjustment_factor_id (adjustment_factor_id),
  UNIQUE KEY uk_adjustment_revision (instrument_id, trade_date, source_name, revision_no),
  UNIQUE KEY uk_adjustment_hash (instrument_id, trade_date, source_name, observation_hash)
);
CREATE TABLE market_db.derived_indicator (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  derived_indicator_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market_sync_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  as_of_date DATE NOT NULL,
  indicator_name VARCHAR(64) NOT NULL,
  window_key VARCHAR(64) NOT NULL,
  source_scope VARCHAR(64) NOT NULL,
  value_decimal DECIMAL(30,12) NULL,
  value_cent BIGINT NULL,
  currency CHAR(3) NULL,
  input_hash BINARY(32) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_derived_indicator_derived_indicator_id (derived_indicator_id),
  UNIQUE KEY uk_derived_indicator (instrument_id, as_of_date, indicator_name, window_key, source_scope, input_hash),
  CHECK ((value_decimal IS NOT NULL AND value_cent IS NULL) OR (value_decimal IS NULL AND value_cent IS NOT NULL AND currency IS NOT NULL))
);
CREATE TABLE market_db.basis_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  basis_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  underlying_instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  future_instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market_sync_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  trade_date DATE NOT NULL,
  source_name VARCHAR(64) NOT NULL,
  revision_no SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  spot_price_points DECIMAL(24,8) NOT NULL,
  future_price_points DECIMAL(24,8) NOT NULL,
  basis_points DECIMAL(24,8) NOT NULL,
  annualized_basis_decimal DECIMAL(20,12) NULL,
  maturity_date DATE NULL,
  days_left SMALLINT UNSIGNED NULL,
  supersedes_basis_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  observation_hash BINARY(32) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_basis_snapshot_basis_snapshot_id (basis_snapshot_id),
  UNIQUE KEY uk_basis_revision (underlying_instrument_id, future_instrument_id, trade_date, source_name, revision_no),
  UNIQUE KEY uk_basis_hash (underlying_instrument_id, future_instrument_id, trade_date, source_name, observation_hash)
);
CREATE TABLE market_db.market_source_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  market_source_event_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market_sync_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  source_name VARCHAR(64) NULL,
  event_type VARCHAR(64) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  error_code VARCHAR(64) NULL,
  error_summary VARCHAR(1000) NOT NULL,
  import_export_file_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_market_source_event_market_source_event_id (market_source_event_id),
  KEY idx_source_event_run (market_sync_run_id, created_at DESC)
);

CREATE TABLE strategy_db.strategy_rule_version (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  strategy_rule_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  strategy_key VARCHAR(64) NOT NULL,
  rule_version VARCHAR(64) NOT NULL,
  rule_hash BINARY(32) NOT NULL,
  rule_json JSON NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_strategy_rule_version_strategy_rule_version_id (strategy_rule_version_id),
  UNIQUE KEY uk_strategy_rule_version (strategy_key, rule_version),
  UNIQUE KEY uk_strategy_rule_hash (strategy_key, rule_hash)
);
CREATE TABLE strategy_db.strategy_evaluation (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  strategy_evaluation_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  strategy_rule_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  input_version VARCHAR(128) NOT NULL,
  as_of_at DATETIME(3) NOT NULL,
  result_json JSON NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_strategy_evaluation_strategy_evaluation_id (strategy_evaluation_id),
  KEY idx_strategy_evaluation_owner_time (owner_user_id, as_of_at DESC)
);
CREATE TABLE strategy_db.signal_run (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  signal_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  strategy_rule_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  job_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  input_version VARCHAR(128) NOT NULL,
  status VARCHAR(24) NOT NULL,
  started_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_signal_run_signal_run_id (signal_run_id),
  UNIQUE KEY uk_signal_run_job (job_id)
);
CREATE TABLE strategy_db.strategy_signal (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  strategy_signal_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  signal_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  signal_type VARCHAR(32) NOT NULL,
  rank_no SMALLINT UNSIGNED NOT NULL,
  explanation VARCHAR(2000) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_strategy_signal_strategy_signal_id (strategy_signal_id),
  UNIQUE KEY uk_strategy_signal (signal_run_id, instrument_id, signal_type)
);
CREATE TABLE strategy_db.backtest_run (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  backtest_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  strategy_rule_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  parameter_hash BINARY(32) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status VARCHAR(24) NOT NULL,
  input_version VARCHAR(128) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL,
  UNIQUE KEY uk_backtest_run_backtest_run_id (backtest_run_id),
  UNIQUE KEY uk_backtest_run (owner_user_id, strategy_rule_version_id, parameter_hash, start_date, end_date)
);
CREATE TABLE strategy_db.backtest_result (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  backtest_result_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  backtest_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  metric_name VARCHAR(64) NOT NULL,
  value_decimal DECIMAL(30,12) NULL,
  value_cent BIGINT NULL,
  currency CHAR(3) NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_backtest_result_backtest_result_id (backtest_result_id),
  UNIQUE KEY uk_backtest_result (backtest_run_id, metric_name, currency),
  CHECK ((value_decimal IS NOT NULL AND value_cent IS NULL) OR (value_decimal IS NULL AND value_cent IS NOT NULL AND currency IS NOT NULL))
);

CREATE TABLE reporting_db.report_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  report_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  report_type VARCHAR(64) NOT NULL,
  currency CHAR(3) NOT NULL,
  as_of_date DATE NOT NULL,
  input_version VARCHAR(128) NOT NULL,
  import_export_file_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  checksum BINARY(32) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_report_snapshot_report_snapshot_id (report_snapshot_id),
  UNIQUE KEY uk_report_snapshot (owner_user_id, report_type, currency, as_of_date, input_version)
);

CREATE TABLE platform_db.async_job (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  job_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  job_type VARCHAR(64) NOT NULL,
  dedupe_key VARCHAR(256) NOT NULL,
  active_dedupe_key VARCHAR(256) NULL,
  status VARCHAR(24) NOT NULL,
  payload_json JSON NOT NULL,
  attempt_no SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  max_attempts SMALLINT UNSIGNED NOT NULL,
  next_run_at DATETIME(3) NOT NULL,
  lease_owner VARCHAR(128) NULL,
  lease_token CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  lease_expires_at DATETIME(3) NULL,
  result_json JSON NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_async_job_job_id (job_id),
  UNIQUE KEY uk_async_job_active (job_type, active_dedupe_key),
  KEY idx_async_job_ready (status, next_run_at),
  KEY idx_async_job_owner (owner_user_id),
  CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','DEGRADED','FAILED','NEEDS_REVIEW','CANCELLED'))
);
CREATE TABLE platform_db.outbox_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  outbox_event_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  event_subject_type VARCHAR(64) NOT NULL,
  event_subject_reference VARCHAR(320) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  event_version INT UNSIGNED NOT NULL,
  payload_json JSON NOT NULL,
  status VARCHAR(16) NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  published_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_outbox_event_outbox_event_id (outbox_event_id),
  UNIQUE KEY uk_outbox_event (event_subject_type, event_subject_reference, event_type, event_version),
  KEY idx_outbox_status (status, occurred_at)
);
CREATE TABLE platform_db.idempotency_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  idempotency_record_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  http_method VARCHAR(8) NOT NULL,
  canonical_path VARCHAR(256) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash BINARY(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  processing_token CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  response_status SMALLINT UNSIGNED NULL,
  response_json JSON NULL,
  response_reference VARCHAR(320) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_idempotency_record_idempotency_record_id (idempotency_record_id),
  UNIQUE KEY uk_idempotency_request (owner_user_id, http_method, canonical_path, idempotency_key),
  CHECK (status IN ('PROCESSING','SUCCEEDED','FAILED'))
);
CREATE TABLE platform_db.audit_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  audit_log_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  actor_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_reference VARCHAR(320) NULL,
  action VARCHAR(64) NOT NULL,
  result VARCHAR(32) NOT NULL,
  trace_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  detail_json JSON NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_audit_log_audit_log_id (audit_log_id),
  KEY idx_audit_actor_time (actor_user_id, created_at DESC),
  KEY idx_audit_resource (resource_type, resource_reference)
);
CREATE TABLE platform_db.feature_flag (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  feature_flag_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  flag_key VARCHAR(128) NOT NULL,
  flag_value_json JSON NOT NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_feature_flag_feature_flag_id (feature_flag_id),
  UNIQUE KEY uk_feature_flag_key (flag_key)
);
CREATE TABLE platform_db.import_export_file (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  import_export_file_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  direction VARCHAR(16) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  content_sha256 BINARY(32) NOT NULL,
  media_type VARCHAR(128) NOT NULL,
  byte_size BIGINT UNSIGNED NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NULL,
  UNIQUE KEY uk_import_export_file_import_export_file_id (import_export_file_id),
  UNIQUE KEY uk_import_export_content (owner_user_id, direction, content_sha256),
  KEY idx_import_export_owner_time (owner_user_id, created_at DESC)
);
