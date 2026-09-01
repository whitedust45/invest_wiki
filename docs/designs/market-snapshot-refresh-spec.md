# 市场快照刷新与导入优先规格

状态：local 已实现并通过集成验收；最后确认：2026-07-31。

## 1. 接口背景与功能

**背景**：策略只能读取已确认的本地市场快照。此前缺少快照写入链路，QQQ 与 IC/IM 会正确降级为 `DATA_STALE`，但无法完成本地数据闭环。

**功能**：管理员可导入已核验的市场数据；XXL-JOB 在每日刷新时先采用同一业务日的导入数据，再由 Tushare Pro 仅补齐缺失字段；所有有效数据写入现有 `market_db` 的不可变快照，随后才评估四类策略。

**使用者**：微信小程序市场页、XXL-JOB、策略评估服务、local 测试种子。

**交互模式**：小程序 HTTPS REST 提交异步快照任务；同进程 application port 调度；XXL-JOB 触发每日任务；Tushare Pro HTTPS REST 仅由 infrastructure adapter 调用。

**关联接口**：`GET/POST /api/v1/strategies/*`、`POST /api/v1/strategies/scans`、既有 MinIO 上传能力；新增 `/api/v1/market/snapshot-submissions`、`/api/v1/market/sync-runs/{marketSyncRunId}`、市场标的查询和数据源代码维护接口。

## 2. 接口定义

### 2.1 管理员提交快照导入

`POST /api/v1/market/snapshot-submissions`（`202`）

```json
{
  "tradingDate":"2026-07-31",
  "sourceName":"BROKER_EXPORT_ATTESTED",
  "sourceReference":"local://market/2026-07-31/broker-export.json",
  "quotes":[{"instrumentId":"01K...","quoteTime":"2026-07-31T07:00:00Z","priceCent":"55231","prevCloseCent":"54980","currency":"USD","sourceObservationKey":"QQQ-20260731"}],
  "metrics":[{"instrumentId":"01K...","metricName":"PB_PERCENTILE","metricValueDecimal":"24.60","sourceObservationKey":"000905-pb-pctl-20260731"}],
  "basis":[{"underlyingInstrumentId":"01K...","futureInstrumentId":"01K...","spotPricePoints":"7582.39","futurePricePoints":"7531.80","annualizedBasisDecimal":"0.0500","maturityDate":"2026-09-18","daysLeft":49,"sourceObservationKey":"IC2609-20260731"}]
}
```

响应：`{ "marketSnapshotSubmissionId":"<ULID>", "marketSyncRunId":"<ULID>", "status":"QUEUED" }`。

所有金额仍为 `*_cent` 十进制整数字符串；指数点位、PB 分位和年化贴水是非货币量，使用十进制字符串。

### 2.2 状态查询与市场维护

- `GET /api/v1/market/sync-runs/{marketSyncRunId}`：当前返回运行状态与起止时间；各来源尝试、写入数、字段级失败与策略可用性详情为 **TBD（后续只读审计扩展）**。不返回 token。
- `GET /api/v1/market/instruments?limit&cursor`：返回稳定游标页及可选 `tushareCode`、`underlyingInstrumentId`。
- `PUT /api/v1/market/instruments/{instrumentId}/source-codes/TUSHARE_PRO`：管理员设置不可空的 Tushare 代码；覆盖更新保留 `updatedAt` 审计。

### 2.3 数据模型增量

- `market_db.market_snapshot_submission`：自增 `id`、`market_snapshot_submission_id`、`submitted_by_user_id`、`market_sync_run_id`、`trading_date`、`source_name`、`source_reference`、`status`、`created_at`、`applied_at`。无外键。
- 三张已规范化明细表：`market_snapshot_submission_quote`、`market_snapshot_submission_metric`、`market_snapshot_submission_basis`。每张表都有自增 `id` 和实体同名 ULID 业务主键，并以 `market_snapshot_submission_id` 关联；字段分别与 `quote_snapshot`、`daily_metric`、`basis_snapshot` 一一对应，不以 JSON 混合存储异构事实。
- 复用既有 `market_db.instrument.ts_code` 作为可空的 Tushare Pro 代码单一真源；不得新增语义重复的 `tushare_code` 列。CFFEX 合约的 `underlying_instrument_id` 必须指向一条非可交易 `INDEX` 标的。新增 `INDEX` 资产类型，只能作为行情/估值/贴水基础，账本不得交易它。
- 所有关联都用语义化 ULID，且不建立外键。所有金融金额列遵守 `*_cent BIGINT` 规则。

## 3. 业务规则与边界条件

### 3.1 参数校验

- P-1：非管理员、未知标的、无效 ULID、业务日为未来日期、空观察集合或重复观察键 → `422 MARKET_SNAPSHOT_INVALID`。
- P-2：报价/金额不是正的 `*_cent` 字符串、报价币种与标的原币种不一致、PB 分位不在 `[0,100]`、贴水点位/年化值不可解析、`daysLeft` 与 `maturityDate` 不一致 → `422 MONEY_CONVENTION_VIOLATION` 或 `422 MARKET_SNAPSHOT_INVALID`。
- P-3：导入源名称、外部观察键或来源引用缺失 → `422 MARKET_SNAPSHOT_INVALID`。系统只把经管理员提交并留痕的导入视为可靠导入，不把浏览器缓存视为来源。
- P-4：Tushare 未启用、token 缺失、代码映射不存在或权限不足 → 不拒绝已通过校验的导入；缺字段写 `market_source_event`，受影响策略为 `DATA_STALE`。

### 3.2 核心业务规则

- B-1：同一 `market_sync_run` 中，导入观察值优先级为 `100`，Tushare Pro 为 `50`。对同一字段/标的/业务日，导入已提供时自动源不得覆盖或另写替代值；缺失字段才允许 Tushare 补齐。
- B-2：快照任务必须按 `QUEUED → RUNNING → SUCCEEDED | FAILED` 推进。`SUCCEEDED` 表示任务与已写入数据可审计，不表示所有策略字段齐全；字段缺失由策略级 `DATA_STALE` 表达。终态不可覆盖。
- B-3：提交接口只入队和持久化经过校验的导入事实；外部 HTTP 只能由 worker/XXL-JOB 调用，不得在 HTTP 请求线程运行。
- B-4：每一条 Tushare 请求与每个导入源分别追加 `market_sync_attempt` 与 `market_source_event`。请求日志和响应日志不得记录 `TUSHARE_TOKEN`；只记录 API 名、代码、交易日、耗时、HTTP/业务错误摘要与来源。
- B-5：运行完成后，策略仅读取该业务日前最近一条 `SUCCEEDED` 的 run，且只使用该 run 内的完整必需字段；不得从较早 run 拼接字段。周末、节假日沿用最近成功交易日。
- B-6：任务先应用导入快照，再调用 Tushare，再标记 run 成功，最后创建四策略评估。写入任一步的数据库事务失败时 run 置 `FAILED`，不产生成功评估。
- B-7：Tushare 代码来自 `instrument.tushare_code`，不得由 `symbol` 猜测。需要 IC/IM PB/贴水时，期货必须有明确的 `underlying_instrument_id`，且指数标的也必须配置 Tushare 代码。

### 3.3 Tushare 字段映射

| 标的/用途 | Tushare API | 必需字段 | 写入 |
|---|---|---|---|
| A 股、ETF | `daily` / `fund_daily` | `trade_date`,`close`,`pre_close` | `quote_snapshot`；完整 OHLC `daily_bar` 写入为 **TBD** |
| QQQ、QLD | `us_daily` | `trade_date`,`close`,`pre_close` | `quote_snapshot`；完整 OHLC `daily_bar` 写入为 **TBD** |
| IC/IM 合约 | `fut_daily`、`fut_basic` | 合约、收盘/结算、到期日 | `quote_snapshot`、合约校验；完整 OHLC `daily_bar` 写入为 **TBD** |
| 中证500/1000指数 | `index_daily`、`index_dailybasic` | 收盘、`pb` | `quote_snapshot`、`daily_metric`；本地历史 PB 计算 `PB_PERCENTILE`；完整 OHLC `daily_bar` 写入为 **TBD** |

Tushare 的返回价格只能在精确乘以 `100` 后为整数时写入 `*_cent`；否则记录 `PRICE_PRECISION_UNREPRESENTABLE`，不四舍五入。`PB_PERCENTILE` 按同一指数已确认的本地 PB 历史（默认 10 年、包含当前值）计算；历史不足时不写该指标。

### 3.4 降级、重试与安全

- D-1：导入成功但自动源失败 → 导入字段仍可用；缺少的字段仅影响对应策略，其他策略继续评估。
- D-2：Tushare 网络、限流、认证或权限错误 → 本轮记录来源事件，不改写旧快照，不重试交易；下个计划周期或管理员重新提交可重试。
- D-3：无导入且 Tushare 未配置 → 任务正常结束并记录 `TUSHARE_UNAVAILABLE`，策略保持 `DATA_STALE`。
- D-4：token 仅从环境变量 `TUSHARE_TOKEN` 读取；local `.env` 不提交；生产环境用部署密钥注入。默认连接/整体超时由环境变量配置，默认分别为 5 秒/20 秒。

## 4. Code 映射

| code / HTTP | 触发规则 | 可重试 |
|---|---|---|
| `0 / 202,200` | B-1～B-7、D-1～D-3 | 是 |
| `MARKET_SNAPSHOT_INVALID / 422` | P-1、P-3 | 修正后 |
| `MONEY_CONVENTION_VIOLATION / 422` | P-2 | 修正后 |
| `ADMIN_REQUIRED / 403` | P-1 | 否 |
| `MARKET_SYNC_NOT_FOUND / 404` | 运行不存在 | 否 |
| `DATA_STALE / 200` | D-1～D-3 | 数据就绪后 |

## 5. 时序与测试矩阵

1. 管理员导入 QQQ 报价、IC/IM PB 和贴水 → 入队；worker 写导入事实。
2. worker 对缺少的 QLD、指数/期货日线调用 Tushare → 只写缺字段。
3. worker 标记 run `SUCCEEDED` → 调用策略评估 → 小程序轮询 run 并刷新策略详情。

| 场景 | 预期 | 层级 |
|---|---|---|
| 同字段导入与 Tushare 同时存在 | 导入值保留，自动源不覆盖 | Unit + MySQL integration |
| QQQ/QLD 自动补齐 | 价格精确转 minor unit，QQQ 评估不再因价格 `DATA_STALE` | Integration |
| PB 历史不足 | 不写 PB 分位，IC/IM `DATA_STALE` | Unit |
| Tushare 401/429/超时 | 记录来源事件，不覆盖历史，不生成交易 | Unit + adapter contract |
| 一个策略字段失败 | 其他完整策略照常评估 | Integration |
| CFFEX 缺少指数锚点 | 422 或 `DATA_STALE`，不写无语义 basis | Unit |
| 微信市场页提交、轮询和策略页刷新 | API 读回与 UI 状态一致 | 开发者工具 E2E |

## 6. 外部依赖与验收边界

唯一自动源为 Tushare Pro `https://api.tushare.pro`。每次请求使用标准 JSON body：`api_name`、`token`、`params`、`fields`；其 token、权限与积分阈值由实际账号决定，启动时不假定任一高级接口可用。Tushare 的 A 股日线、每日指标、指数日线和期货合约契约分别以官方文档为准：<https://tushare.pro/document/1?doc_id=27>、<https://tushare.pro/document/2?doc_id=32>、<https://tushare.pro/document/1?doc_id=95>、<https://tushare.pro/document/2?doc_id=135>。

本地可稳定验证：导入优先级、金额精度、状态机、provider JSON 解析、失败降级、MySQL 写入和四策略评估。需要真实链路验证：用户账号对 `us_daily`、`fut_daily`、`index_dailybasic` 的实际权限、速率限制和源数据质量；缺少授权时系统必须安全降级，而不是使用未文档化抓取源。

## 7. 风险处置

- R-1：Tushare 的高阶接口可能无权限。处置：接口粒度记录失败，策略降级；导入路径始终可用。
- R-2：PB 分位依赖长历史。处置：首次刷新可加载/分段保存历史；历史不足明确 `DATA_STALE`。
- R-3：IC/IM 贴水必须有真实指数锚点。处置：新增不可交易 `INDEX` 标的并显式关联，禁止用期货自身或无效 ID 代替。
- R-4：原有 8 标的 local seed 不包含两个指数锚点。完整 IC/IM 自动快照 fixture 需增加“中证500”“中证1000”两条 `INDEX` 标的，种子标的数由 8 调整为 10，账务交易数仍为 23。
