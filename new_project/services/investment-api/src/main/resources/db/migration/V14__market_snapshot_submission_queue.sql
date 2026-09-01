-- Reliable market imports are persisted before any external refresh is attempted.  All links use semantic ULIDs;
-- no foreign keys are used because market facts are retained independently for audit and replay.
CREATE TABLE market_db.market_snapshot_submission (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  market_snapshot_submission_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  submitted_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market_sync_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  trading_date DATE NOT NULL,
  source_name VARCHAR(64) NOT NULL,
  source_reference VARCHAR(512) NOT NULL,
  status VARCHAR(24) NOT NULL,
  applied_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_market_snapshot_submission_id (market_snapshot_submission_id),
  UNIQUE KEY uk_market_snapshot_submission_run (market_sync_run_id),
  KEY idx_market_snapshot_submission_ready (status, created_at),
  CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED'))
);

CREATE TABLE market_db.market_snapshot_submission_quote (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  market_snapshot_submission_quote_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market_snapshot_submission_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  quote_time DATETIME(3) NOT NULL,
  source_observation_key VARCHAR(256) NOT NULL,
  price_cent BIGINT NOT NULL,
  prev_close_cent BIGINT NULL,
  currency CHAR(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_market_snapshot_submission_quote_id (market_snapshot_submission_quote_id),
  UNIQUE KEY uk_market_snapshot_submission_quote_observation
    (market_snapshot_submission_id, instrument_id, source_observation_key),
  KEY idx_market_snapshot_submission_quote_submission (market_snapshot_submission_id),
  CHECK (price_cent > 0),
  CHECK (prev_close_cent IS NULL OR prev_close_cent > 0)
);

CREATE TABLE market_db.market_snapshot_submission_metric (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  market_snapshot_submission_metric_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market_snapshot_submission_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  metric_name VARCHAR(64) NOT NULL,
  metric_value_decimal DECIMAL(30,12) NOT NULL,
  source_observation_key VARCHAR(256) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_market_snapshot_submission_metric_id (market_snapshot_submission_metric_id),
  UNIQUE KEY uk_market_snapshot_submission_metric_observation
    (market_snapshot_submission_id, instrument_id, metric_name, source_observation_key),
  KEY idx_market_snapshot_submission_metric_submission (market_snapshot_submission_id)
);

CREATE TABLE market_db.market_snapshot_submission_basis (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  market_snapshot_submission_basis_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  market_snapshot_submission_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  underlying_instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  future_instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  spot_price_points DECIMAL(24,8) NOT NULL,
  future_price_points DECIMAL(24,8) NOT NULL,
  annualized_basis_decimal DECIMAL(20,12) NULL,
  maturity_date DATE NULL,
  days_left SMALLINT UNSIGNED NULL,
  source_observation_key VARCHAR(256) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_market_snapshot_submission_basis_id (market_snapshot_submission_basis_id),
  UNIQUE KEY uk_market_snapshot_submission_basis_observation
    (market_snapshot_submission_id, future_instrument_id, source_observation_key),
  KEY idx_market_snapshot_submission_basis_submission (market_snapshot_submission_id)
);

GRANT SELECT, INSERT, UPDATE ON market_db.market_snapshot_submission TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON market_db.market_snapshot_submission_quote TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON market_db.market_snapshot_submission_metric TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON market_db.market_snapshot_submission_basis TO '${app_mysql_username}'@'%';
