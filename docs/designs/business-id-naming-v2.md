# 业务 ID 语义化命名规范（v3）

## 强制规则

- 每张自研表必须同时具备 `id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY` 与下表指定的 `*_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE`。
- 后者是全局唯一 ULID，是 API、消息、日志、对象路径和跨表映射的唯一标识；自增 `id` 不得离开本表。
- 关系列使用**目标实体**的业务 ID 名称，禁止 `biz_id`、`*_biz_id`、`parent_id`、`related_id`、`resource_id` 与 `aggregate_id`；不建立外键。
- 多态审计、幂等响应与未来 Outbox 不保存泛化关系 ID：使用仅供展示和追溯的 `*_reference VARCHAR` 文本，例如 `ledger.transaction:01...`。它不得参与领域查询、权限判断或跨表映射；事件载荷内仍使用具体的 `transaction_id`、`job_id` 等语义字段。

## 38 张表的主业务 ID

| 逻辑库 | 表 | 业务主键 |
| --- | --- | --- |
| identity | iam_user | `user_id` |
| identity | iam_wechat_identity | `wechat_identity_id` |
| identity | iam_login_audit | `login_audit_id` |
| ledger | ledger_account | `account_id` |
| ledger | ledger_transaction | `transaction_id` |
| ledger | ledger_posting | `posting_id` |
| ledger | ledger_trade_detail | `trade_detail_id` |
| ledger | ledger_snapshot | `ledger_snapshot_id` |
| portfolio | portfolio_position | `position_id` |
| portfolio | portfolio_position_lot | `position_lot_id` |
| portfolio | portfolio_manual_valuation | `manual_valuation_id` |
| portfolio | portfolio_daily_snapshot | `daily_snapshot_id` |
| market | instrument | `instrument_id` |
| market | instrument_alias | `instrument_alias_id` |
| market | watchlist | `watchlist_id` |
| market | watchlist_item | `watchlist_item_id` |
| market | market_sync_run | `market_sync_run_id` |
| market | market_sync_attempt | `market_sync_attempt_id` |
| market | quote_snapshot | `quote_snapshot_id` |
| market | daily_bar | `daily_bar_id` |
| market | daily_metric | `daily_metric_id` |
| market | adjustment_factor | `adjustment_factor_id` |
| market | derived_indicator | `derived_indicator_id` |
| market | basis_snapshot | `basis_snapshot_id` |
| market | market_source_event | `market_source_event_id` |
| strategy | strategy_rule_version | `strategy_rule_version_id` |
| strategy | strategy_evaluation | `strategy_evaluation_id` |
| strategy | signal_run | `signal_run_id` |
| strategy | strategy_signal | `strategy_signal_id` |
| strategy | backtest_run | `backtest_run_id` |
| strategy | backtest_result | `backtest_result_id` |
| reporting | report_snapshot | `report_snapshot_id` |
| platform | async_job | `job_id` |
| platform | outbox_event | `outbox_event_id` |
| platform | idempotency_record | `idempotency_record_id` |
| platform | audit_log | `audit_log_id` |
| platform | feature_flag | `feature_flag_id` |
| platform | import_export_file | `import_export_file_id` |

## 关系命名示例

`owner_user_id`、`created_by_user_id`、`account_id`、`transaction_id`、`reversal_of_transaction_id`、`correction_root_transaction_id`、`trade_detail_id`、`source_trade_detail_id`、`instrument_id`、`position_id`、`watchlist_id`、`market_sync_run_id`、`job_id`、`strategy_rule_version_id`、`import_export_file_id`、`supersedes_quote_snapshot_id`。

同一实体的自引用也保留完整语义，例如 `supersedes_daily_bar_id`，而不是缩写为 `parent_id` 或泛化为 `related_id`。索引和唯一键名称可以简写，但字段名不得失去业务含义。

`operation_group_key` 是单次复合操作的不可解析相关键，不是任何表的业务 ID，也不得跨限界上下文用作实体映射。
