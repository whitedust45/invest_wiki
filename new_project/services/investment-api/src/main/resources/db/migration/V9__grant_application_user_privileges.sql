-- Execute only with the migration account: it must have GRANT OPTION.
-- The application account is pre-provisioned by local Compose or cloud database provisioning.
-- Keep this migration table-scoped: append-only facts must never receive UPDATE or DELETE.

GRANT SELECT, INSERT, UPDATE ON identity_db.iam_user TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON identity_db.iam_wechat_identity TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON identity_db.iam_login_audit TO '${app_mysql_username}'@'%';

GRANT SELECT, INSERT, UPDATE ON ledger_db.ledger_account TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON ledger_db.ledger_state TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON ledger_db.ledger_transaction TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON ledger_db.ledger_posting TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON ledger_db.ledger_trade_detail TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON ledger_db.ledger_snapshot TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON ledger_db.ledger_corporate_action TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON ledger_db.ledger_income_detail TO '${app_mysql_username}'@'%';

GRANT SELECT, INSERT, UPDATE, DELETE ON portfolio_db.portfolio_position TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON portfolio_db.portfolio_position_lot TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON portfolio_db.portfolio_daily_snapshot TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON portfolio_db.futures_position TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON portfolio_db.futures_position_lot TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON portfolio_db.portfolio_manual_valuation TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON portfolio_db.portfolio_reconciliation TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON portfolio_db.portfolio_reconciliation_position TO '${app_mysql_username}'@'%';

GRANT SELECT, INSERT, UPDATE ON market_db.instrument TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON market_db.instrument_alias TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON market_db.option_contract TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON market_db.futures_contract TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON market_db.watchlist TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON market_db.watchlist_item TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON market_db.market_sync_run TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON market_db.market_sync_attempt TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON market_db.quote_snapshot TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON market_db.daily_bar TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON market_db.daily_metric TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON market_db.adjustment_factor TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON market_db.derived_indicator TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON market_db.basis_snapshot TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON market_db.market_source_event TO '${app_mysql_username}'@'%';

GRANT SELECT, INSERT, UPDATE ON platform_db.async_job TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON platform_db.outbox_event TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON platform_db.idempotency_record TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT ON platform_db.audit_log TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON platform_db.feature_flag TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON platform_db.import_export_file TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON platform_db.import_preview TO '${app_mysql_username}'@'%';

GRANT SELECT, INSERT, UPDATE ON strategy_db.strategy_rule_version TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON strategy_db.strategy_evaluation TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON strategy_db.signal_run TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON strategy_db.strategy_signal TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON strategy_db.backtest_run TO '${app_mysql_username}'@'%';
GRANT SELECT, INSERT, UPDATE ON strategy_db.backtest_result TO '${app_mysql_username}'@'%';
GRANT SELECT ON reporting_db.report_snapshot TO '${app_mysql_username}'@'%';
