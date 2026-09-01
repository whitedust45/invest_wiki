-- Strategy scans are persistent owner-scoped read jobs. They do not create market data or ledger transactions.
CREATE TABLE strategy_db.strategy_scan (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  strategy_scan_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  requested_strategy_keys_json JSON NOT NULL,
  as_of_at DATETIME(3) NOT NULL,
  status VARCHAR(24) NOT NULL,
  attempt_no SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  started_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL,
  result_json JSON NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_strategy_scan_id (strategy_scan_id),
  KEY idx_strategy_scan_owner_time (owner_user_id, created_at DESC),
  KEY idx_strategy_scan_ready (status, started_at, created_at),
  CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','PARTIAL_SUCCEEDED','FAILED','SKIPPED_STALE'))
);

CREATE TABLE strategy_db.strategy_scan_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  strategy_scan_item_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  strategy_scan_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  strategy_key VARCHAR(64) NOT NULL,
  strategy_evaluation_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(64) NULL,
  failure_message VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_strategy_scan_item_id (strategy_scan_item_id),
  UNIQUE KEY uk_strategy_scan_item_scan_key (strategy_scan_id, strategy_key),
  KEY idx_strategy_scan_item_scan (strategy_scan_id, created_at),
  CHECK (strategy_key IN ('HIGH_DIVIDEND', 'QQQ_GROWTH', 'IC_IM', 'DEEP_PUT')),
  CHECK (status IN ('IN_RANGE','WATCH','BLOCKED','DATA_STALE','CROSS_CURRENCY_UNVALUED','FAILED'))
);

GRANT SELECT, INSERT, UPDATE ON strategy_db.strategy_scan TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON strategy_db.strategy_scan_item TO '${app_mysql_username}'@'%';
