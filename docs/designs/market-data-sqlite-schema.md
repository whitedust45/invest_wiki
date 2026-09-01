# 市场数据 SQLite 长期库设计

状态：已确认第一版关系骨架  
日期：2026-07-05  
目标数据库：`apps/dashboard/data/market-data.db`

## 背景

当前 dashboard 的行情与估值数据主要以 JSON 作为前端读取层：

- `apps/dashboard/data/position-quotes.json`
- `apps/dashboard/data/position-history.json`
- `apps/dashboard/data/ic-im-valuation.json`

这些 JSON 适合前端展示和静态回退，但不适合作为长期可追溯的数据事实库。后续需要追溯数据来源、同步批次、失败原因、多来源差异、修正次数和历史导入，因此新增独立 SQLite 市场数据库。

现有 `apps/dashboard/data/ledger.db` 只保存账本快照，市场数据不与其混用。

## 已确认决策

- SQLite 是市场数据长期事实库，JSON 是前端投影和缓存。
- 第一阶段覆盖当前持仓 + 手工观察池，不做全市场。
- 初始导入现有 JSON，作为 `legacy_import` seed；当前 JSON 多为测试数据，但导入逻辑仍要求幂等。
- 不使用外键约束。
- 表之间使用冗余业务 Key 关联，例如 `instrument_key`、`symbol`、`trade_date`、`run_id`、`source`。
- 允许普通索引和唯一索引用于查询加速与幂等去重。
- 同一标的、同一交易日、同一数据源的事实值被修正时，覆盖当前行，并维护 `first_seen_at`、`last_seen_at`、`revision_count`。
- 多来源数据全部保留；前端 JSON 导出时再按优先级投影。
- IC/IM 指数估值、PB 分位、贴水和移仓窗口纳入同一个市场数据关系模型。

## 领域模型

市场数据领域包括 5 类事实：

1. 标的：股票、ETF、指数、美股 ETF、期货合约、汇率对。
2. 行情：日线 OHLCV、最新报价快照。
3. 估值/指标：PE、PB、股息率、换手率、成交额等。
4. 复权：前复权/后复权计算所需因子。
5. 期货贴水：IC/IM 合约、现货点位、期货价格、年化贴水、移仓窗口。

## 关系骨架

```text
tracked_instruments
  -> quote_snapshots
  -> daily_bars
  -> daily_metrics
  -> adjustment_factors
  -> derived_indicators
  -> basis_snapshots

sync_runs
  -> quote_snapshots.run_id
  -> daily_bars.run_id
  -> daily_metrics.run_id
  -> adjustment_factors.run_id
  -> derived_indicators.run_id
  -> basis_snapshots.run_id
  -> source_events.run_id

projection_exports
  -> 记录从 SQLite 导出 JSON 的批次
```

说明：

- 上图是逻辑关系，不建立 SQLite 外键。
- 每张事实表都冗余 `instrument_key`、`symbol`、`market`、`asset_type`、`name` 中的关键字段，避免查询时强依赖 join。
- `sync_runs.run_id` 是追溯 Key，不是外键父表。

## 表设计

### 1. tracked_instruments

标的主数据和跟踪池。覆盖当前持仓、手工观察池、系统需要的指数/汇率/期货合约。

关键关系：

- `instrument_key` 是稳定业务 Key。
- `symbol` 是人类常用代码。
- `track_scope` 决定同步范围。

建议字段：

```sql
CREATE TABLE IF NOT EXISTS tracked_instruments (
  instrument_key TEXT PRIMARY KEY,
  symbol TEXT NOT NULL,
  ts_code TEXT,
  name TEXT,
  market TEXT NOT NULL,
  asset_type TEXT NOT NULL,
  currency TEXT,
  exchange TEXT,
  track_scope TEXT NOT NULL,
  active INTEGER NOT NULL DEFAULT 1,
  source TEXT,
  first_seen_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  raw_json TEXT
);
```

取值约定：

- `instrument_key` 示例：`CN:SZ:000001`、`CN:SH:000905`、`US:NASDAQ:QQQ`、`CFFEX:IC2407`、`FX:USDCNY`
- `asset_type`：`stock`、`etf`、`index`、`us_etf`、`future`、`fx`
- `track_scope`：`holding`、`watchlist`、`system`

索引：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_tracked_instruments_symbol_market
ON tracked_instruments(symbol, market);

CREATE INDEX IF NOT EXISTS idx_tracked_instruments_scope_active
ON tracked_instruments(track_scope, active);
```

### 2. sync_runs

同步批次表。任何脚本拉取、legacy 导入、JSON 投影都应产生批次记录。

```sql
CREATE TABLE IF NOT EXISTS sync_runs (
  run_id TEXT PRIMARY KEY,
  run_type TEXT NOT NULL,
  status TEXT NOT NULL,
  started_at TEXT NOT NULL,
  finished_at TEXT,
  requested_symbols TEXT,
  source_priority_json TEXT,
  params_json TEXT,
  summary_json TEXT,
  error_summary TEXT
);
```

取值约定：

- `run_type`：`quote`、`history`、`valuation`、`basis`、`legacy_import`、`projection_export`
- `status`：`running`、`success`、`partial`、`failed`

### 3. quote_snapshots

保存最新报价快照。它不是严格日线，可能来自盘中、收盘、legacy 快照、Yahoo 汇率折算。

```sql
CREATE TABLE IF NOT EXISTS quote_snapshots (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  instrument_key TEXT NOT NULL,
  symbol TEXT NOT NULL,
  name TEXT,
  market TEXT,
  asset_type TEXT,
  quote_date TEXT NOT NULL,
  quote_time TEXT,
  price REAL NOT NULL,
  raw_price REAL,
  currency TEXT,
  fx_rate REAL,
  prev_close REAL,
  change_pct REAL,
  volume REAL,
  amount REAL,
  source TEXT NOT NULL,
  run_id TEXT NOT NULL,
  first_seen_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  revision_count INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT
);
```

唯一索引：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_quote_snapshots_unique
ON quote_snapshots(symbol, quote_time, source);
```

说明：

- 如果只有日期没有具体时间，`quote_time` 使用 `quote_date`。
- 美股人民币折算价保存在 `price`，原始美元价保存在 `raw_price`，汇率保存在 `fx_rate`。

### 4. daily_bars

日线事实表。股票、ETF、指数、美股 ETF 均进入该表。

```sql
CREATE TABLE IF NOT EXISTS daily_bars (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  instrument_key TEXT NOT NULL,
  symbol TEXT NOT NULL,
  name TEXT,
  market TEXT,
  asset_type TEXT,
  trade_date TEXT NOT NULL,
  open REAL,
  high REAL,
  low REAL,
  close REAL NOT NULL,
  volume REAL,
  amount REAL,
  change_pct REAL,
  adjustment TEXT NOT NULL DEFAULT 'none',
  source TEXT NOT NULL,
  run_id TEXT NOT NULL,
  first_seen_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  revision_count INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT
);
```

唯一索引：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_bars_unique
ON daily_bars(symbol, trade_date, adjustment, source);

CREATE INDEX IF NOT EXISTS idx_daily_bars_symbol_date
ON daily_bars(symbol, trade_date);
```

取值约定：

- `adjustment`：`none`、`qfq`、`hfq`
- 前端 `position-history.json` 默认投影 `qfq` 口径；若只有 `none`，允许回退。

### 5. daily_metrics

日频估值和基础指标。IC/IM 指数估值进入该表。

```sql
CREATE TABLE IF NOT EXISTS daily_metrics (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  instrument_key TEXT NOT NULL,
  symbol TEXT NOT NULL,
  name TEXT,
  market TEXT,
  asset_type TEXT,
  trade_date TEXT NOT NULL,
  metric_name TEXT NOT NULL,
  metric_value REAL,
  metric_unit TEXT,
  source TEXT NOT NULL,
  run_id TEXT NOT NULL,
  first_seen_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  revision_count INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT
);
```

唯一索引：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_metrics_unique
ON daily_metrics(symbol, trade_date, metric_name, source);

CREATE INDEX IF NOT EXISTS idx_daily_metrics_lookup
ON daily_metrics(symbol, metric_name, trade_date);
```

指标命名：

- `pe`
- `pe_ttm`
- `pb`
- `dividend_yield`
- `turnover_rate`
- `amount`
- `volume`

### 6. adjustment_factors

复权因子表。复权因子独立保存，避免把复权计算和原始日线混在同一事实里。

```sql
CREATE TABLE IF NOT EXISTS adjustment_factors (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  instrument_key TEXT NOT NULL,
  symbol TEXT NOT NULL,
  name TEXT,
  market TEXT,
  asset_type TEXT,
  trade_date TEXT NOT NULL,
  adj_factor REAL NOT NULL,
  source TEXT NOT NULL,
  run_id TEXT NOT NULL,
  first_seen_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  revision_count INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT
);
```

唯一索引：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_adjustment_factors_unique
ON adjustment_factors(symbol, trade_date, source);
```

### 7. derived_indicators

派生指标缓存。用于保存 PB 分位、滚动收益、策略判断值等可重算但前端常用的数据。

```sql
CREATE TABLE IF NOT EXISTS derived_indicators (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  instrument_key TEXT NOT NULL,
  symbol TEXT NOT NULL,
  name TEXT,
  market TEXT,
  asset_type TEXT,
  as_of_date TEXT NOT NULL,
  indicator_name TEXT NOT NULL,
  indicator_value REAL,
  indicator_text TEXT,
  window TEXT,
  source_scope TEXT NOT NULL,
  run_id TEXT NOT NULL,
  first_seen_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  revision_count INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT
);
```

唯一索引：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_derived_indicators_unique
ON derived_indicators(symbol, as_of_date, indicator_name, source_scope);
```

指标命名：

- `pb_percentile`
- `pe_percentile`
- `rolling_return`
- `drawdown`
- `risk_bucket`

说明：

- `daily_metrics` 保存输入事实，例如 PB。
- `derived_indicators` 保存基于历史窗口计算出的结果，例如 PB 分位。

### 8. basis_snapshots

IC/IM 期货贴水表。贴水有合约维度、到期日、剩余天数，不适合塞进通用 `daily_metrics`。

```sql
CREATE TABLE IF NOT EXISTS basis_snapshots (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  underlying_key TEXT NOT NULL,
  underlying_symbol TEXT NOT NULL,
  underlying_name TEXT,
  future_key TEXT NOT NULL,
  future_symbol TEXT NOT NULL,
  trade_date TEXT NOT NULL,
  spot_price REAL,
  future_price REAL,
  basis REAL,
  annualized_basis_pct REAL,
  maturity_date TEXT,
  days_left INTEGER,
  roll_window INTEGER NOT NULL DEFAULT 0,
  roll_alert INTEGER NOT NULL DEFAULT 0,
  source TEXT NOT NULL,
  run_id TEXT NOT NULL,
  first_seen_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  revision_count INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT
);
```

唯一索引：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_basis_snapshots_unique
ON basis_snapshots(underlying_symbol, future_symbol, trade_date, source);

CREATE INDEX IF NOT EXISTS idx_basis_snapshots_underlying_date
ON basis_snapshots(underlying_symbol, trade_date);
```

### 9. source_events

数据源事件表。记录错误、降级、权限不足、HTTP 403、Tushare 接口无权限、fallback 原因。

```sql
CREATE TABLE IF NOT EXISTS source_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  run_id TEXT NOT NULL,
  event_time TEXT NOT NULL,
  event_type TEXT NOT NULL,
  severity TEXT NOT NULL,
  instrument_key TEXT,
  symbol TEXT,
  source TEXT,
  api_name TEXT,
  message TEXT NOT NULL,
  detail_json TEXT
);
```

唯一索引：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_source_events_unique
ON source_events(run_id, symbol, source, api_name, event_type);
```

取值约定：

- `event_type`：`error`、`fallback`、`permission_denied`、`empty_result`、`rate_limited`
- `severity`：`info`、`warn`、`error`

### 10. projection_exports

JSON 投影导出记录。用于追踪某个 JSON 是从哪批事实数据导出的。

```sql
CREATE TABLE IF NOT EXISTS projection_exports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  projection_name TEXT NOT NULL,
  generated_at TEXT NOT NULL,
  output_path TEXT NOT NULL,
  run_id TEXT NOT NULL,
  row_count INTEGER NOT NULL DEFAULT 0,
  source_priority_json TEXT,
  params_json TEXT,
  summary_json TEXT
);
```

索引：

```sql
CREATE INDEX IF NOT EXISTS idx_projection_exports_name_time
ON projection_exports(projection_name, generated_at);
```

## JSON 投影规则

### position-quotes.json

来源表：

- 主：`quote_snapshots`
- 兜底：`daily_bars` 最新交易日 close

优先级：

```text
先按最新 quote_date / quote_time 选择；同一时间点再按 Tushare > legacy_json > Sina > Tencent > Yahoo
```

保留原 JSON schema：

```text
generated_at
source
quotes
errors
```

### position-history.json

来源表：

- `daily_bars`

优先级：

```text
qfq Tushare > qfq legacy_json > qfq Tencent > none Tushare > none legacy_json > none Tencent
```

保留原 JSON schema：

```text
generated_at
source
histories
errors
```

### ic-im-valuation.json

来源表：

- `daily_metrics`
- `derived_indicators`
- `basis_snapshots`
- `source_events`

投影内容：

- `pe` / `pb` 来自 `daily_metrics`
- `pe_percentile` / `pb_percentile` 来自 `derived_indicators`
- `basis.contracts` 来自 `basis_snapshots`
- 权限不足、字段缺失、fallback 原因来自 `source_events`

## 写入策略

所有事实表采用幂等 upsert。

逻辑：

1. 根据唯一索引定位事实行。
2. 不存在则插入，`revision_count = 0`。
3. 存在且事实值未变化，只更新 `last_seen_at`。
4. 存在且事实值变化，覆盖当前事实值，`revision_count = revision_count + 1`。
5. 原始返回尽量放入 `raw_json`，方便回溯。

第一阶段不新增 revision 明细表。

## Legacy JSON 导入规则

导入来源：

- `apps/dashboard/data/position-quotes.json`
- `apps/dashboard/data/position-history.json`
- `apps/dashboard/data/ic-im-valuation.json`

规则：

- 每次导入生成 `sync_runs.run_type = legacy_import`。
- JSON 中的标的写入或更新 `tracked_instruments`。
- quote 写入 `quote_snapshots`，`source` 优先使用原 quote 的 `source`，缺失时使用 `legacy_json`。
- history 写入 `daily_bars`，`adjustment` 默认为 `qfq`，缺失字段留空。
- IC/IM PE/PB 写入 `daily_metrics`。
- IC/IM PE/PB 分位写入 `derived_indicators`。
- IC/IM 贴水写入 `basis_snapshots`。
- JSON 的错误字段写入 `source_events`。

## 第一阶段实现边界

第一阶段只做：

- 建库建表。
- legacy JSON 幂等导入。
- 行情刷新后写入 SQLite。
- 从 SQLite 导出原有三个 JSON。
- 本地 API 继续返回原 JSON schema。
- wiki 优秀公司/观察池 A 股标的通过后台 job 异步刷新，浏览器只提交任务和轮询状态。

第一阶段不做：

- 全市场同步。
- revision 明细表。
- 复杂数据清洗后台任务。
- 外键约束。
- 强制新增第三方依赖。

## 本地服务异步刷新接口

Python 本地服务提供三个市场数据后台接口：

```text
GET  /api/market-data/watchlist
POST /api/market-data/refresh-wiki?days=520
GET  /api/market-data/jobs/<job_id>
```

`watchlist` 从以下 wiki 页面抽取 A 股代码并去重：

```text
knowledge/wiki/portfolios/a-share-good-companies-list.md
knowledge/wiki/portfolios/high-dividend-cashflow-watchlist.md
```

抽取出的 `track_scope` 和 `source_wiki_path` 会先写入 `tracked_instruments`。后续普通行情 upsert 不覆盖更具体的 wiki scope/source，保证标的和 wiki 来源之间的关系可追溯。

`refresh-wiki` 不在 HTTP handler 内同步抓取行情，而是提交 `modules.market_data.api.MarketDataJobRegistry` 后台任务。任务内部调用 `modules/market_data/quotes.py`，写入 `market-data.db`，再从 SQLite 导出 `position-quotes.json` 和 `position-history.json`。任务状态保留在内存中，已完成任务按上限清理，避免本地服务长期运行时无限增长。

如果本地服务配置了 `APP_ACCESS_KEY`，`/api/market-data/*` 接口必须携带同一个访问密钥。这样本地开发可以零配置使用，后续部署到云服务器时也不会暴露可触发抓取和子进程的接口。

## Tushare 接入策略

数据源优先级保持为：

```text
Tushare > legacy_json > Sina > Tencent > Yahoo
```

Tushare 客户端选择策略：

- 若本地安装官方 `tushare` SDK，优先通过 SDK 的 `pro_api(token).query(...)` 调用。
- 若未安装 SDK，自动回退到官方 Tushare Pro HTTP API。
- Token 只从 `services/sync/.env` 或环境变量 `TUSHARE_TOKEN` 读取，不写入仓库。
- 抓取失败、权限不足、空结果等事件写入 `source_events`，并在本轮 JSON 投影的 `errors` 字段保留给前端和 job 结果读取。

## 后续扩展点

- 增加 `financial_statements` 保存财报指标。
- 增加 `dividend_events` 保存分红事件。
- 增加 `watchlist_memberships` 管理多个观察池。
- 增加 `daily_bar_revisions` 保存每次修正前后的差异。
- 增加数据质量检查，例如缺口检测、异常涨跌幅检测、来源差异检测。
