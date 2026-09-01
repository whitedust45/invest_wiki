-- Phase 2 foundation. V1/V2 are immutable and this migration only applies to an empty Phase 1 ledger.
-- Each guard fails closed so an instance containing business facts requires a dedicated data migration.
DROP PROCEDURE IF EXISTS platform_db.assert_phase2_v3_preconditions;
DELIMITER $$
CREATE PROCEDURE platform_db.assert_phase2_v3_preconditions()
BEGIN
  IF EXISTS (SELECT 1 FROM ledger_db.ledger_transaction LIMIT 1)
      OR EXISTS (SELECT 1 FROM ledger_db.ledger_posting LIMIT 1)
      OR EXISTS (SELECT 1 FROM ledger_db.ledger_trade_detail LIMIT 1)
      OR EXISTS (SELECT 1 FROM ledger_db.ledger_snapshot LIMIT 1)
      OR EXISTS (SELECT 1 FROM portfolio_db.portfolio_position LIMIT 1)
      OR EXISTS (SELECT 1 FROM portfolio_db.portfolio_position_lot LIMIT 1)
      OR EXISTS (SELECT 1 FROM portfolio_db.portfolio_manual_valuation LIMIT 1)
      OR EXISTS (SELECT 1 FROM portfolio_db.portfolio_daily_snapshot LIMIT 1)
      OR EXISTS (SELECT 1 FROM platform_db.import_export_file LIMIT 1) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V3 requires an empty Phase 1 ledger; use a dedicated data migration';
  END IF;

  IF NOT EXISTS (
      SELECT 1 FROM information_schema.table_constraints
      WHERE constraint_schema = 'ledger_db'
        AND table_name = 'ledger_account'
        AND constraint_name = 'ledger_account_chk_1'
        AND constraint_type = 'CHECK') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V3 expected ledger_account_chk_1; inspect SHOW CREATE TABLE before migration';
  END IF;

  IF NOT EXISTS (
      SELECT 1 FROM information_schema.statistics
      WHERE table_schema = 'platform_db'
        AND table_name = 'import_export_file'
        AND index_name = 'uk_import_export_content') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V3 expected uk_import_export_content; inspect import_export_file before migration';
  END IF;
END$$
DELIMITER ;

CALL platform_db.assert_phase2_v3_preconditions();
DROP PROCEDURE platform_db.assert_phase2_v3_preconditions;

ALTER TABLE ledger_db.ledger_transaction
  ADD COLUMN ledger_version BIGINT UNSIGNED NOT NULL,
  ADD UNIQUE KEY uk_ledger_transaction_owner_version (owner_user_id, ledger_version),
  ADD CONSTRAINT ck_ledger_transaction_type CHECK (transaction_type IN ('EXTERNAL_FUNDING','EXTERNAL_WITHDRAWAL','INTERNAL_TRANSFER','TRADE_BUY','TRADE_SELL','DIVIDEND','INTEREST','FEE','FUTURES_OPEN','FUTURES_CLOSE','FUTURES_MARGIN','FUTURES_DAILY_SETTLEMENT','OPTION_OPEN','OPTION_CLOSE','OPTION_EXPIRE','CORPORATE_ACTION','REVERSAL')),
  ADD CONSTRAINT ck_ledger_transaction_source_type CHECK (source_type IN ('MANUAL','IMPORT','CORRECTION_REVERSAL','CORRECTION_REPLACEMENT'));

ALTER TABLE ledger_db.ledger_trade_detail
  ADD COLUMN fee_cent BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN option_contract_multiplier BIGINT NULL,
  ADD CONSTRAINT ck_ledger_trade_detail_fee_cent CHECK (fee_cent >= 0),
  ADD CONSTRAINT ck_ledger_trade_detail_option_multiplier CHECK (option_contract_multiplier IS NULL OR option_contract_multiplier > 0);

ALTER TABLE ledger_db.ledger_account
  DROP CHECK ledger_account_chk_1,
  ADD CONSTRAINT ck_ledger_account_kind CHECK (account_kind IN ('ASSET_CASH','ASSET_MARGIN','ASSET_INVESTMENT','ASSET_CLEARING','EQUITY_EXTERNAL','INCOME_DIVIDEND','INCOME_INTEREST','EXPENSE_FEE','EXPENSE_WITHHOLDING_TAX','EXPENSE_OPTION','PNL_REALIZED'));

ALTER TABLE platform_db.import_export_file
  DROP INDEX uk_import_export_content,
  ADD KEY idx_import_export_owner_direction_hash (owner_user_id, direction, content_sha256),
  ADD COLUMN encryption_key_version VARCHAR(128) NOT NULL,
  ADD CONSTRAINT ck_import_export_file_direction CHECK (direction IN ('IMPORT','RECONCILIATION_EVIDENCE')),
  ADD CONSTRAINT ck_import_export_file_status CHECK (status IN ('UPLOAD_PENDING','QUARANTINED','SCANNED','PREVIEWED','COMMITTED','DELETED','FAILED'));

CREATE TABLE ledger_db.ledger_state (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  ledger_state_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  ledger_version BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ledger_state_id (ledger_state_id),
  UNIQUE KEY uk_ledger_state_owner (owner_user_id)
);

CREATE TABLE market_db.option_contract (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  option_contract_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  underlying_instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  option_right VARCHAR(4) NOT NULL,
  strike_price_cent BIGINT NOT NULL,
  contract_multiplier BIGINT NOT NULL,
  currency CHAR(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_option_contract_id (option_contract_id),
  UNIQUE KEY uk_option_contract_instrument (instrument_id),
  KEY idx_option_contract_underlying (underlying_instrument_id),
  CHECK (option_right IN ('PUT','CALL')),
  CHECK (strike_price_cent > 0),
  CHECK (contract_multiplier > 0)
);

CREATE TABLE market_db.futures_contract (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  futures_contract_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  product_code VARCHAR(2) NOT NULL,
  contract_multiplier_cent BIGINT NOT NULL,
  currency CHAR(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_futures_contract_id (futures_contract_id),
  UNIQUE KEY uk_futures_contract_instrument (instrument_id),
  KEY idx_futures_contract_product (product_code),
  CHECK (product_code IN ('IC','IM')),
  CHECK (contract_multiplier_cent > 0)
);

CREATE TABLE portfolio_db.futures_position (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  futures_position_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  locked_margin_account_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  open_quantity DECIMAL(24,8) NOT NULL,
  average_open_price_points DECIMAL(24,8) NOT NULL,
  contract_multiplier_cent BIGINT NOT NULL,
  currency CHAR(3) NOT NULL,
  source_ledger_version BIGINT UNSIGNED NOT NULL,
  projection_version BIGINT UNSIGNED NOT NULL,
  calculated_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_futures_position_id (futures_position_id),
  UNIQUE KEY uk_futures_position_owner_margin_instrument (owner_user_id, locked_margin_account_id, instrument_id),
  CHECK (open_quantity >= 0),
  CHECK (open_quantity = FLOOR(open_quantity)),
  CHECK (contract_multiplier_cent > 0)
);

CREATE TABLE portfolio_db.futures_position_lot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  futures_position_lot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  futures_position_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_trade_detail_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  opened_on DATE NOT NULL,
  opened_quantity DECIMAL(24,8) NOT NULL,
  remaining_quantity DECIMAL(24,8) NOT NULL,
  open_price_points DECIMAL(24,8) NOT NULL,
  last_settlement_price_points DECIMAL(24,8) NOT NULL,
  contract_multiplier_cent BIGINT NOT NULL,
  allocated_initial_margin_cent BIGINT NOT NULL,
  remaining_initial_margin_cent BIGINT NOT NULL,
  currency CHAR(3) NOT NULL,
  source_ledger_version BIGINT UNSIGNED NOT NULL,
  calculated_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_futures_position_lot_id (futures_position_lot_id),
  UNIQUE KEY uk_futures_position_lot_source (source_trade_detail_id),
  KEY idx_futures_position_lot_position (futures_position_id, opened_on),
  CHECK (opened_quantity > 0),
  CHECK (opened_quantity = FLOOR(opened_quantity)),
  CHECK (remaining_quantity >= 0 AND remaining_quantity <= opened_quantity),
  CHECK (remaining_quantity = FLOOR(remaining_quantity)),
  CHECK (last_settlement_price_points > 0),
  CHECK (contract_multiplier_cent > 0),
  CHECK (allocated_initial_margin_cent > 0),
  CHECK (remaining_initial_margin_cent >= 0 AND remaining_initial_margin_cent <= allocated_initial_margin_cent)
);

ALTER TABLE portfolio_db.portfolio_position_lot
  ADD COLUMN opened_cost_cent BIGINT NOT NULL,
  ADD COLUMN remaining_cost_cent BIGINT NOT NULL,
  ADD CHECK (opened_cost_cent >= 0),
  ADD CHECK (remaining_cost_cent >= 0 AND remaining_cost_cent <= opened_cost_cent);

CREATE TABLE ledger_db.ledger_corporate_action (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  corporate_action_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  transaction_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  action_type VARCHAR(24) NOT NULL,
  effective_on DATE NOT NULL,
  ratio_numerator BIGINT UNSIGNED NOT NULL,
  ratio_denominator BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ledger_corporate_action_id (corporate_action_id),
  UNIQUE KEY uk_ledger_corporate_action_transaction (transaction_id),
  KEY idx_ledger_corporate_action_instrument_date (instrument_id, effective_on),
  CHECK (action_type IN ('STOCK_SPLIT','REVERSE_SPLIT','STOCK_DIVIDEND')),
  CHECK (ratio_numerator > 0 AND ratio_denominator > 0)
);

CREATE TABLE ledger_db.ledger_income_detail (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  income_detail_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  transaction_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  income_type VARCHAR(16) NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  entitlement_date DATE NULL,
  gross_amount_cent BIGINT NOT NULL,
  tax_withheld_cent BIGINT NOT NULL DEFAULT 0,
  per_share_amount_cent BIGINT NULL,
  currency CHAR(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ledger_income_detail_id (income_detail_id),
  UNIQUE KEY uk_ledger_income_detail_transaction (transaction_id),
  KEY idx_ledger_income_detail_instrument_date (instrument_id, entitlement_date),
  CHECK (income_type IN ('DIVIDEND','INTEREST')),
  CHECK (gross_amount_cent > 0 AND tax_withheld_cent >= 0 AND tax_withheld_cent <= gross_amount_cent),
  CHECK (per_share_amount_cent IS NULL OR per_share_amount_cent > 0),
  CHECK ((income_type = 'DIVIDEND' AND instrument_id IS NOT NULL AND entitlement_date IS NOT NULL) OR income_type = 'INTEREST')
);

CREATE TABLE portfolio_db.portfolio_reconciliation (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  reconciliation_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  cash_account_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reconciliation_date DATE NOT NULL,
  broker_cash_cent BIGINT NOT NULL,
  ledger_cash_cent BIGINT NOT NULL,
  cash_difference_cent BIGINT NOT NULL,
  cash_difference_direction VARCHAR(16) NOT NULL,
  currency CHAR(3) NOT NULL,
  status VARCHAR(16) NOT NULL,
  discrepancy_reason VARCHAR(1000) NULL,
  attachment_import_export_file_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
  source_ledger_version BIGINT UNSIGNED NOT NULL,
  created_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_portfolio_reconciliation_id (reconciliation_id),
  KEY idx_portfolio_reconciliation_account_date (cash_account_id, reconciliation_date DESC),
  KEY idx_portfolio_reconciliation_owner_date (owner_user_id, reconciliation_date DESC),
  CHECK (status IN ('MATCHED','NEEDS_REVIEW')),
  CHECK (broker_cash_cent >= 0 AND ledger_cash_cent >= 0),
  CHECK (cash_difference_direction IN ('NONE','BROKER_GREATER','LEDGER_GREATER')),
  CHECK ((cash_difference_cent = 0 AND cash_difference_direction = 'NONE') OR (cash_difference_cent > 0 AND cash_difference_direction IN ('BROKER_GREATER','LEDGER_GREATER'))),
  CHECK ((status = 'MATCHED' AND cash_difference_cent = 0 AND discrepancy_reason IS NULL) OR status = 'NEEDS_REVIEW')
);

CREATE TABLE portfolio_db.portfolio_reconciliation_position (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  reconciliation_position_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reconciliation_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  instrument_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  broker_quantity DECIMAL(24,8) NOT NULL,
  ledger_quantity DECIMAL(24,8) NOT NULL,
  quantity_difference DECIMAL(24,8) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_portfolio_reconciliation_position_id (reconciliation_position_id),
  UNIQUE KEY uk_portfolio_reconciliation_position (reconciliation_id, instrument_id),
  KEY idx_portfolio_reconciliation_position_reconciliation (reconciliation_id),
  CHECK (broker_quantity >= 0 AND ledger_quantity >= 0),
  CHECK (quantity_difference = broker_quantity - ledger_quantity)
);

CREATE TABLE platform_db.import_preview (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  import_preview_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  job_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  import_export_file_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  import_format VARCHAR(32) NOT NULL,
  source_snapshot_id VARCHAR(64) NULL,
  mapping_json JSON NOT NULL,
  preview_json JSON NOT NULL,
  preview_checksum BINARY(32) NOT NULL,
  status VARCHAR(24) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_import_preview_id (import_preview_id),
  UNIQUE KEY uk_import_preview_job (job_id),
  KEY idx_import_preview_owner_expiry (owner_user_id, expires_at),
  CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','NEEDS_REVIEW','COMMITTED','EXPIRED'))
);
