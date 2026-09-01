-- The strategy tables existed as unused scaffolding only. Their rows are test data and are intentionally
-- discarded before introducing mandatory owner attribution and immutable evaluation input hashes.
DELETE FROM strategy_db.backtest_result;
DELETE FROM strategy_db.backtest_run;
DELETE FROM strategy_db.strategy_signal;
DELETE FROM strategy_db.signal_run;
DELETE FROM strategy_db.strategy_evaluation;
DELETE FROM strategy_db.strategy_rule_version;

ALTER TABLE strategy_db.strategy_rule_version
  ADD COLUMN owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL AFTER strategy_rule_version_id,
  DROP INDEX uk_strategy_rule_version,
  DROP INDEX uk_strategy_rule_hash,
  ADD UNIQUE KEY uk_strategy_rule_version_owner_key_version (owner_user_id, strategy_key, rule_version),
  ADD UNIQUE KEY uk_strategy_rule_hash_owner_key (owner_user_id, strategy_key, rule_hash),
  ADD KEY idx_strategy_rule_version_owner_key_created (owner_user_id, strategy_key, created_at DESC);

CREATE TABLE strategy_db.strategy_active_rule (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  strategy_active_rule_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  strategy_key VARCHAR(64) NOT NULL,
  strategy_rule_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  binding_version BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_strategy_active_rule_id (strategy_active_rule_id),
  UNIQUE KEY uk_strategy_active_rule_owner_key (owner_user_id, strategy_key)
);

CREATE TABLE strategy_db.strategy_active_rule_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  strategy_active_rule_event_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  strategy_key VARCHAR(64) NOT NULL,
  previous_strategy_rule_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  next_strategy_rule_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  binding_version BIGINT UNSIGNED NOT NULL,
  created_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_strategy_active_rule_event_id (strategy_active_rule_event_id),
  UNIQUE KEY uk_strategy_active_rule_event_owner_key_version (owner_user_id, strategy_key, binding_version)
);

ALTER TABLE strategy_db.strategy_evaluation
  MODIFY COLUMN input_version VARCHAR(512) NOT NULL,
  ADD COLUMN status VARCHAR(32) NOT NULL AFTER as_of_at,
  ADD COLUMN input_hash BINARY(32) NOT NULL AFTER input_version,
  ADD UNIQUE KEY uk_strategy_evaluation_input (owner_user_id, strategy_rule_version_id, input_hash);

ALTER TABLE strategy_db.strategy_signal
  ADD COLUMN strategy_evaluation_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL AFTER strategy_signal_id,
  MODIFY COLUMN signal_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  MODIFY COLUMN instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD COLUMN signal_scope VARCHAR(16) NOT NULL AFTER instrument_id,
  ADD COLUMN signal_key VARCHAR(128) NOT NULL AFTER signal_scope,
  ADD COLUMN severity VARCHAR(16) NOT NULL AFTER signal_type,
  ADD COLUMN as_of_at DATETIME(3) NOT NULL AFTER explanation,
  MODIFY COLUMN rank_no SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  DROP INDEX uk_strategy_signal,
  ADD UNIQUE KEY uk_strategy_signal_evaluation_key (strategy_evaluation_id, signal_key),
  ADD KEY idx_strategy_signal_run (signal_run_id),
  ADD KEY idx_strategy_signal_evaluation_time (strategy_evaluation_id, as_of_at DESC);

CREATE TABLE strategy_db.strategy_reference_nav (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  strategy_reference_nav_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  strategy_key VARCHAR(64) NOT NULL,
  currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reference_nav_cent BIGINT NOT NULL,
  as_of_at DATETIME(3) NOT NULL,
  valid_until DATETIME(3) NOT NULL,
  source VARCHAR(32) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_strategy_reference_nav_id (strategy_reference_nav_id),
  UNIQUE KEY uk_strategy_reference_nav_owner_key_currency_as_of (owner_user_id, strategy_key, currency, as_of_at),
  KEY idx_strategy_reference_nav_owner_key_as_of (owner_user_id, strategy_key, as_of_at DESC),
  CHECK (currency = 'USD'),
  CHECK (reference_nav_cent > 0),
  CHECK (valid_until >= as_of_at)
);

CREATE TABLE strategy_db.strategy_seed_run (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  strategy_seed_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  seed_name VARCHAR(64) NOT NULL,
  fixture_checksum BINARY(32) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_strategy_seed_run_id (strategy_seed_run_id),
  UNIQUE KEY uk_strategy_seed_run_owner_name (owner_user_id, seed_name)
);

ALTER TABLE ledger_db.ledger_transaction
  ADD COLUMN strategy_key VARCHAR(64) NULL AFTER transaction_type,
  ADD KEY idx_ledger_transaction_strategy (owner_user_id, strategy_key, occurred_on, ledger_version),
  ADD CONSTRAINT ck_ledger_transaction_strategy_key CHECK
    (strategy_key IS NULL OR strategy_key IN ('HIGH_DIVIDEND', 'QQQ_GROWTH', 'IC_IM', 'DEEP_PUT'));

GRANT SELECT, INSERT, UPDATE ON strategy_db.strategy_active_rule TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON strategy_db.strategy_active_rule_event TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON strategy_db.strategy_reference_nav TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON strategy_db.strategy_seed_run TO '${app_mysql_username}'@'%';
