# 四类策略完整功能迁移规格

状态：实现推进中；日期：2026-07-31。

## 当前实现边界（2026-07-31）

- 已完成：四策略工作区摘要与历史分页、版本化规则/活动指针审计、USD 参考净值追加事实、基于规则/账本/本地市场输入版本的不可变评估、四类策略纯计算器、策略归属账本事实、期货原子移仓、移仓预览草稿操作组标识、XXL-JOB 已确认输入的每日评估入口，以及微信小程序的策略/账本预览确认界面。
- 已完成验证：MySQL Flyway V1–V14 实际迁移、后端单元测试、local Testcontainers 集成测试、小程序 TypeScript 类型检查、微信开发者工具编译和本地 API 联调。
- 已完成：市场快照刷新链路。V14 提供可信导入队列、导入优先于 Tushare Pro 的缺字段补齐、行情/指标/贴水的不可变事实及来源审计；`LEGACY_FULL_PATH` 仅在 local profile 提供 10 标的、23 笔完整账务路径、同一快照 run 和四份规则与评估的测试种子。策略只消费已落库且成功的快照；没有最近成功批次、报价、PB、贴水或参考净值时仍保持 `BLOCKED` / `DATA_STALE`，不会伪造交易信号。

## 已确认的决策

- 完整迁移旧 Dashboard 已有的高分红、QQQ、IC/IM、深度 Put 四类策略能力；不新增历史回测执行器。
- 旧 Dashboard 的规则和阈值成为首个可编辑规则版本；旧流水、估值与行情只作为 local 测试种子，覆盖全部交易与结算路径。
- 不接券商 API。策略只读取本地行情/估值快照；XXL-JOB 每日凌晨刷新，缺失或过期只返回 `DATA_STALE`，不形成可执行信号。
- 所有金额按原币种两位小数的最小单位以 `*_cent BIGINT`/Java `long` 存储，API 用十进制字符串。旧 `annualExpense=12`、`newMoney=1` 分别迁为 `CNY 12,000,000 cent`、`CNY 1,000,000 cent`。
- 不换汇、不混合币种：高分红与 IC/IM 只使用 CNY 资金池；QQQ 与深度 Put 只使用用户显式维护的 USD 策略参考净值与 USD 资金池。

## 1. 接口背景与功能

**背景**：旧 Dashboard 的策略规则、估值、持仓、流水和压力检查散落在浏览器 localStorage 中；小程序需要完整可用的策略工作区，同时账务事实仍必须由 Java 账本服务唯一写入。

**功能**：提供四类策略的版本化规则、不可变评估快照、非交易型状态信号、策略专属工作区和 local 测试种子；所有买卖、分红、保证金、结算、移仓、期权到期仍复用既有账本预览与提交接口，并以不可变 `strategyKey` 归属账本事实。

**使用者**：微信小程序策略页、策略详情分包、XXL-JOB、后端集成测试。

**交互模式**：小程序 HTTPS REST；同进程 DDD application port；XXL-JOB 定时触发；无 MQ、无券商 API、无自动下单。

**关联接口**：既有 `/api/v1/ledger/transactions/preview`、`/api/v1/ledger/transactions`、`/api/v1/portfolio/summary`、`/api/v1/portfolio/manual-valuations`、`/api/v1/jobs/{jobId}`。

**策略键**：`HIGH_DIVIDEND`、`QQQ_GROWTH`、`IC_IM`、`DEEP_PUT`。

## 2. 接口定义

### 2.1 工作区与规则接口

| 方法与路径 | 请求要点 | 成功响应要点 | 说明 |
|---|---|---|---|
| `GET /api/v1/strategies` | 无 | 四张策略摘要、最新评估状态、规则版本、输入时间 | 空账本仍返回四张卡片。 |
| `GET /api/v1/strategies/{strategyKey}/workspace` | `strategyKey` | 规则、最新评估、信号、策略资金池、账本摘要、可录入动作 | 详情页唯一读取模型。 |
| `GET /api/v1/strategies/{strategyKey}/rule-versions` | `limit`、`cursor` | 版本稳定游标页 | 只读历史版本。 |
| `POST /api/v1/strategies/{strategyKey}/rule-versions` | `ruleVersion`、`rule`、`expectedActiveRuleVersionId` | 新 `strategyRuleVersionId`、`status=ACTIVE` | 管理员创建后立即成为唯一活动版本；旧版本归档但不可改。 |
| `GET /api/v1/strategies/{strategyKey}/reference-nav` | `limit`、`cursor` | USD 参考净值稳定游标页 | 仅 QQQ 和深度 Put 支持。 |
| `POST /api/v1/strategies/{strategyKey}/reference-nav` | `referenceNavCent`、`currency=USD`、`asOfAt`、`validUntil` | 新 `strategyReferenceNavId` | 追加用户显式维护的 USD 参考净值事实，不修改规则版本。 |
| `POST /api/v1/strategies/{strategyKey}/evaluations` | 可选 `asOfDate`；禁止传行情数值 | `strategyEvaluationId`、`inputVersion`、`status`、`result` | 只基于已确认账本和已存市场快照生成不可变评估。 |
| `POST /api/v1/strategies/scans` | `strategyKeys`（可省略，默认四类） | `202`、`strategyScanId` | 仅触发对已存快照的异步批量评估；不发起外部行情请求。 |
| `GET /api/v1/strategies/scans/{strategyScanId}` | `strategyScanId` | 队列状态、逐策略评估关联或失败原因 | 与历史导入专用的 `GET /api/v1/jobs/{jobId}` 分离，避免异构响应冲突。 |
| `GET /api/v1/strategies/{strategyKey}/evaluations` | `limit`、`cursor` | 评估快照稳定游标页 | 用于历史曲线与审计。 |
| `POST /api/v1/development/strategy-test-seed` | `seedSet=LEGACY_FULL_PATH` | 已创建的账户、标的、交易、估值、评估数量 | 仅 `local` profile、仅空账本、仅当前管理员；生产与体验环境路由不存在。 |

所有请求均需要 Bearer 管理员会话，所有业务 ID 都是 26 位 ULID。分页返回统一为 `{items, nextCursor}`。

### 2.1.1 账本 `FUTURES_ROLL` 命令扩展

在既有 `POST /api/v1/ledger/transactions/preview` 与 `POST /api/v1/ledger/transactions` 上新增**命令** `transactionType=FUTURES_ROLL`。它不是一条落库交易类型：预览和提交请求必须分别提供 `closeLeg` 与 `openLeg`，每一腿沿用既有期货平仓/开仓字段和校验；提交成功后只落两条事实 `FUTURES_CLOSE`、`FUTURES_OPEN`，并返回共同的 `operationGroupKey` 和两条 `transactions`。

- 两腿必须在同一数据库事务内完成。任一腿的合约、数量、交割日、保证金、现金账户或账本版本校验失败时，整体失败且不写入任一腿。
- 预览响应必须展示两条拟写入事实和拟定的 `operationGroupKey`；提交请求仍遵循既有“预览版本未变、用户确认后提交”规则。
- `FUTURES_ROLL` 只接受 CFFEX 的 IC/IM 多头合约；不生成名义价值金额，也不得用一条 `FEE` 或浏览器本地记录替代两腿。

### 2.1.2 账本策略归属上下文

既有账本交易创建/预览/修正请求增加可选 `strategyKey`（四个受控策略键之一）。从策略工作区进入的所有提交必须填写它；通用账本入口可缺省，表示“不属于策略工作区”。该字段随每条落库交易保存为不可变事实，作为策略工作区过滤流水、持仓和现金流的唯一依据，不能仅放在小程序缓存或备注中。

- `FUTURES_ROLL` 的请求级 `strategyKey` 必须为 `IC_IM`，并复制到平仓、开仓两条事实。
- 修正策略归属的交易时，替代交易必须继承原 `strategyKey`；策略归属错误的历史事实只能通过“冲正 + 同归属替代”保留审计链，不提供 UPDATE 改标签接口。
- 不填 `strategyKey` 的既有通用账本接口保持兼容；策略评估不把未归属流水臆测分配给任一策略。

### 2.2 核心对象字段

| 对象 | 字段 | 约束 |
|---|---|---|
| RuleVersion | `strategyRuleVersionId`、`strategyKey`、`ruleVersion`、`rule`、`status`、`createdAt` | `ruleVersion` 在同一 owner/策略内唯一；`rule` 为对象，金额键仅允许 `*_cent` 字符串及显式 `currency`。 |
| Evaluation | `strategyEvaluationId`、`strategyRuleVersionId`、`inputVersion`、`asOfAt`、`status`、`result` | `inputVersion` 包含账本版本、市场快照版本和 USD 策略参考净值版本；评估永不覆盖。 |
| Signal | `strategySignalId`、`strategyEvaluationId`、`signalType`、`severity`、`explanation`、`asOfAt` | `signalScope` 为 `STRATEGY` 或 `INSTRUMENT`；策略级信号不绑定标的。`signalType` 枚举为 `IN_RANGE`、`WATCH`、`BLOCKED`、`DATA_STALE`、`CROSS_CURRENCY_UNVALUED`；不是交易指令。 |
| StrategyReferenceNav | `strategyReferenceNavId`、`currency`、`referenceNavCent`、`asOfAt`、`validUntil` | 所有金额均为字符串；只允许 USD，且只用于相同币种策略比例分母。 |
| LocalSeedResult | `seedName`、`createdAccounts`、`createdInstruments`、`createdTransactions`、`createdEvaluations` | 仅测试环境，返回的金额不汇总不同币种。 |

### 2.3 四类规则最小字段

| 策略 | 固定币种 | 必需规则字段 | 旧 Dashboard 迁移逻辑 |
|---|---|---|---|
| 高分红 | CNY | `annual_expense_cent`、`annual_expense_currency`、`minimum_dividend_coverage_percent`、`cash_buffer_months` | `annual_expense_cent=12000000`；过去 12 个月分红/利息与年度支出同币种比较。 |
| QQQ | USD | `starter_percent`、`target_percent`、`upper_percent`、`qld_max_share_percent`、`moving_average_days` | 保留 5%/10%/12% 和 QLD 不超过右尾仓 35%；分母读取独立的 USD 参考净值事实。 |
| IC/IM | CNY | `minimum_pool_cent`、`pb_entry_percentile`、`stress_drop_percent`、`margin_warning_percent`、`roll_window_days` | 保留资金池、PB 分位、压力、保证金与移仓检查；期货点位不是金额。 |
| 深度 Put | USD | `budget_min_percent`、`budget_max_percent`、`expiry_warning_days` | 保留年度保险预算 0.5%–2%、到期梯度和保费归零检查；分母读取独立的 USD 参考净值事实。 |

## 3. 业务规则与边界条件

### 3.1 参数校验

- P-1：`strategyKey` 不在四个受控枚举内 → `404 STRATEGY_NOT_FOUND`。
- P-2：规则版本号为空、超过 64 字符、同策略重复，或 `rule_json` 含 JSON number 金额、`FLOAT` 语义金额、非 `*_cent` 金额键 → `422 STRATEGY_RULE_INVALID`。
- P-3：任何 `*_cent` 不是十进制整数字符串、金额缺少 `currency`、金额为负且无明确方向字段 → `422 MONEY_CONVENTION_VIOLATION`。
- P-4：QQQ/Put 的参考净值币种不是 USD，或高分红/IC/IM 的规则币种不是 CNY → `422 STRATEGY_CURRENCY_INVALID`。
- P-5：评估日期晚于本地业务日、早于可用快照，批量扫描传入重复策略键，或 USD 参考净值 `referenceNavCent` 非正、`validUntil < asOfAt` → `422 STRATEGY_INPUT_INVALID`。
- P-6：测试种子在非 `local` profile、账本非空或非管理员调用 → 分别返回 `404`、`409 SEED_REQUIRES_EMPTY_LEDGER`、`403`。

### 3.2 核心业务规则

- B-1：每个 owner 的每个策略只能有一个活动规则版本。首次创建时 `expectedActiveRuleVersionId` 必须缺省，且仅在不存在 `strategy_active_rule` 绑定时可创建；后续创建时该字段必须等于当前绑定版本 → 原绑定原子切换到新版本。不满足任一条件均返回 `409 RULE_VERSION_CONFLICT`。
- B-2：规则版本、评估、信号和扫描均为追加记录；`strategy_active_rule` 只是可变的当前指针，每次切换必须同步追加 `strategy_active_rule_event`。禁止 UPDATE 评估结果或以“编辑”方式修改账本交易 → 账本修正只能走既有 correction 接口。
- B-3：策略评估分母与分子必须同币种：高分红、IC/IM 为 CNY；QQQ、深度 Put 为 USD。不同币种遇到时 → 保留独立展示，返回 `CROSS_CURRENCY_UNVALUED`，不计算比例。
- B-4：QQQ/Put 的 USD 参考净值必须由用户显式维护，且 `asOfAt ≤ 评估时点 ≤ validUntil`；未填、失效或与资金池币种不符 → `DATA_STALE`，不生成 `IN_RANGE`/`WATCH` 结论。
- B-5：评估输入版本必须同时绑定不可变账本版本、市场快照版本、规则版本和参考净值版本；相同输入可幂等复用同一评估，不同任一版本必须新建评估。
- B-6：高分红评估使用 CNY 已入账的过去 12 个月分红与利息；QQQ 使用 USD QQQ/QLD 持仓和 USD 参考净值；IC/IM 使用 CNY 保证金、已结算损益、PB/贴水/移仓窗口快照；Put 使用 USD 保费、持仓、到期日与 USD 年度预算。
- B-7：策略页录入动作必须调用既有账本“预览 → 用户确认 → 提交”闭环，并携带与页面相同的 `strategyKey`。高分红/QQQ 的现货买卖及分红；Put 的买入、平仓、无价值到期；IC/IM 的保证金、开平仓、逐日结算、`FUTURES_ROLL` 原子移仓。移仓必须同时产生平旧仓、开新仓两条关联事实且共享 `operation_group_key`；没有可验证两腿的遗留 roll 只导入已确认手续费并标记 `ROLL_FEE_ONLY`。策略服务不得创建第二套交易流水，也不得将未归属流水猜测分配给策略。
- B-8：`DATA_STALE`、`BLOCKED`、`WATCH`、`IN_RANGE` 都是解释性状态；小程序必须同时显示输入时间、规则版本和缺失原因，禁止显示“买入”“卖出”等自动交易指令。
- B-8a：四类策略统一采用状态语义：全部硬约束满足才为 `IN_RANGE`；数据完整但任一资金池、比例、预算、保证金或移仓窗口条件未满足/接近边界为 `WATCH`；缺少规则、必需账本事实或标的配置为 `BLOCKED`；行情、PB/贴水、参考净值或估值快照缺失/过期为 `DATA_STALE`；发现不应混算的币种输入为 `CROSS_CURRENCY_UNVALUED`。所有状态仅解释条件，禁止推导交易指令。
- B-8b：行情、PB 分位与贴水仅可读取评估业务日前最近一个 `status=SUCCEEDED` 且已完成的 `market_sync_run`。周末及法定休市不按自然日使该成功交易日快照过期；该批次内缺少策略所需快照时才返回 `DATA_STALE`，不得回退混用更早批次或拉取外部行情。
- B-9：`LEGACY_FULL_PATH` 种子必须覆盖下表所列四类策略动作，并通过现有账本命令与导入校验写入；种子不能绕过货币、标的、保证金、期权到期和复式分录校验。创建前必须取得 owner 级本地种子锁；“账本为空”判断、种子运行记录和全部写入在同一事务中完成，防止并发重复导入。

| 种子策略 | 必须覆盖的账务命令/事实 | 物理交易数 |
|---|---|---:|
| 高分红 | CNY 外部入金、现货买入、现货卖出、现金分红、利息 | 5 |
| QQQ | USD 外部入金、QQQ 买入、QLD 买入、QLD 卖出、QQQ 分红 | 5 |
| 深度 Put | USD 外部入金、两份不同到期日 Put 买入、一份 Put 平仓、另一份到期无价值核销 | 5 |
| IC/IM | CNY 外部入金、保证金转入、IC 开仓、IM 开仓、逐日结算、`FUTURES_ROLL` 的平旧/开新两腿、新 IC 合约平仓 | 8 |
| **合计** | 四类策略完整路径 | **23** |

### 3.3 边界条件

- E-1：没有任何账本交易、市场快照或评估 → 四张策略卡仍返回，状态为 `BLOCKED`，说明为“等待基础数据”。
- E-2：现货、期权或期货标的未配置，或期货合约已到期 → 工作区保留错误原因，录入按钮不可提交；不伪造持仓或估值。
- E-3：某一策略缺数据而其他三类完整 → 批量扫描部分成功；每策略单独保存结果并返回失败列表，不回滚成功策略。
- E-4：同一策略同一输入版本被并发评估 → 通过输入版本唯一性收敛为一个评估；第二请求返回已有评估。

### 3.4 降级策略

- D-1：每日行情刷新失败、快照缺失或快照落后于目标业务日 → 使用最近确认快照仅供阅读，所有依赖字段标记 `DATA_STALE`，不得生成可执行状态。
- D-2：XXL-JOB 不可用 → 手动评估仍可读取已存快照；系统记录任务失败事件，不自动重试交易或补写行情。
- D-3：市场数据只部分可用 → 仅对完整输入的策略输出评估；缺字段的策略输出 `DATA_STALE` 和字段清单。

### 3.5 时序与状态机

- T-1：每日任务必须先完成“市场快照落库并确认版本”，再运行四类策略评估；前一步失败时评估任务结束为 `SKIPPED_STALE`。
- T-2：扫描状态只允许 `QUEUED → RUNNING → SUCCEEDED | PARTIAL_SUCCEEDED | FAILED | SKIPPED_STALE`；终态不可回退。
- T-3：策略交易表单必须先取得账本预览，再使用同一不可变业务请求提交；预览失效或账本版本冲突时必须重新预览。

## 4. Code 映射

| code / HTTP | message | 对应规则 | 可重试 |
|---|---|---|---|
| `0 / 200,201,202` | success | B-1～B-9、E-1、E-3、E-4、D-1～D-3、T-1～T-3 | 仅读取、幂等评估和扫描创建可重试。 |
| `STRATEGY_NOT_FOUND / 404` | strategy not found | P-1 | 否 |
| `STRATEGY_RULE_INVALID / 422` | invalid strategy rule | P-2 | 修正后否 |
| `MONEY_CONVENTION_VIOLATION / 422` | invalid money convention | P-3 | 修正后否 |
| `STRATEGY_CURRENCY_INVALID / 422` | invalid strategy currency | P-4 | 修正后否 |
| `STRATEGY_INPUT_INVALID / 422` | invalid evaluation input | P-5 | 修正后否 |
| `LOCAL_SEED_UNAVAILABLE / 404` | development seed unavailable | P-6（非 local） | 否 |
| `ADMIN_REQUIRED / 403` | administrator role required | P-6（非管理员） | 否 |
| `SEED_REQUIRES_EMPTY_LEDGER / 409` | development seed requires empty ledger | P-6（账本非空） | 重建 local 测试环境后可重试 |
| `RULE_VERSION_CONFLICT / 409` | active rule changed | B-1 | 读取最新版本后重试 |
| `DATA_STALE / 200` | data stale | B-4、B-8、D-1、D-3 | 刷新成功后重试 |
| `CROSS_CURRENCY_UNVALUED / 200` | cross currency not valued | B-3 | 填写同币种输入后重试 |
| `STRATEGY_BLOCKED / 200` | waiting for prerequisite data | E-1、E-2 | 补齐数据后重试 |
| `JOB_UNAVAILABLE / 503` | scheduled scan unavailable | D-2 | XXL-JOB 恢复后重试 |

## 5. 接口 I/O 示例

### 5.1 创建 QQQ 规则版本（B-1、P-2～P-4）

`POST /api/v1/strategies/QQQ_GROWTH/rule-versions`

请求：

```json
{"ruleVersion":"qqq-growth-v1","expectedActiveRuleVersionId":"01JQ7W4R5S6T7V8W9X0Y1Z2ABC","rule":{"starter_percent":"5","target_percent":"10","upper_percent":"12","qld_max_share_percent":"35","moving_average_days":"120"}}
```

响应：

```json
{"code":"0","strategyRuleVersionId":"01JQ7W4R5S6T7V8W9X0Y1Z2ABD","status":"ACTIVE","archivedRuleVersionId":"01JQ7W4R5S6T7V8W9X0Y1Z2ABC"}
```

### 5.2 生成同币种完整评估（B-3～B-6）

`POST /api/v1/strategies/IC_IM/evaluations`

请求：

```json
{"asOfDate":"2026-07-31"}
```

响应：

```json
{"code":"0","strategyEvaluationId":"01JQ7W4R5S6T7V8W9X0Y1Z2ABE","inputVersion":"ledger:0000000000042|market:2026-07-31-r3|rule:01JQ7W4R5S6T7V8W9X0Y1Z2ABD","status":"WATCH","result":{"currency":"CNY","pb_percentile":"28.40","margin_warning_percent":"60","signals":[{"signalType":"WATCH","explanation":"PB 分位已进入观察区；仍需确认压力测试和保证金现金垫。"}]}}
```

### 5.3 USD 参考净值过期的正常降级（B-4、D-1）

`POST /api/v1/strategies/DEEP_PUT/evaluations`

响应：

```json
{"code":"DATA_STALE","strategyEvaluationId":"01JQ7W4R5S6T7V8W9X0Y1Z2ABF","status":"DATA_STALE","result":{"currency":"USD","missingOrStaleFields":["reference_nav_as_of"],"signals":[{"signalType":"DATA_STALE","explanation":"USD 策略参考净值未处于当前有效期，保险预算未计算。"}]}}
```

### 5.4 空工作区（E-1）

`GET /api/v1/strategies/HIGH_DIVIDEND/workspace`

```json
{"code":"STRATEGY_BLOCKED","strategyKey":"HIGH_DIVIDEND","latestEvaluation":null,"availableActions":["CREATE_CNY_CASH_ACCOUNT","RECORD_FUNDING","CREATE_INSTRUMENT"],"message":"等待 CNY 账本和市场快照。"}
```

### 5.5 local 完整测试种子（B-9、P-6）

`POST /api/v1/development/strategy-test-seed`

请求：

```json
{"seedSet":"LEGACY_FULL_PATH"}
```

响应：

```json
{"code":"0","seedName":"LEGACY_FULL_PATH","createdAccounts":3,"createdInstruments":8,"createdTransactions":23,"createdEvaluations":4,"currencies":["CNY","USD"]}
```

## 6. 外部依赖行为约定

### 6.1 账本与组合读模型（同进程 application port）

- **定义**：使用既有账本预览、提交、修正、组合总览和手工估值能力；字段与错误码以 `contracts/openapi/investment-api.yaml` 为唯一真源。
- **一致性**：账本提交成功后其 `ledgerVersion` 与不可变 `strategy_key` 是后续策略评估的输入版本和归属依据；策略服务只读账本，不写交易。
- **失败**：预览或提交失败直接返回既有 Problem；策略工作区保留表单输入，不自动重试，不生成镜像流水。
- **限制**：期货仅 CFFEX IC/IM 多头；期权仅买入型开平仓和无价值到期；沿用既有金额、标的、保证金、到期日校验。为兑现 B-7，本实现必须在既有账本契约、控制器与应用服务中补齐 2.1.1 的 `FUTURES_ROLL` 原子命令。

### 6.2 市场快照（本地 `market_db` 读模型）

- **定义**：策略查询只读取已落库的报价、估值、PB/PE 分位、贴水、到期窗口和数据来源版本；不直接访问外部券商或数据商。
- **一致性**：快照以成功刷新任务确认的 `marketSnapshotVersion` 为准；读取失败或缺字段按 D-1/D-3 降级。
- **限制**：同一标的/日期/来源的唯一约束由市场读模型保证；策略不补造行情。

### 6.3 XXL-JOB（调度入口）

- **定义**：`strategy-market-refresh` 在每日凌晨先刷新本地市场快照，再创建四类策略评估；开发与测试可直接调用 application service，不依赖 Job Admin。
- **失败**：Job Admin 不可用时不会影响已存快照的手动评估；状态按 D-2、T-1、T-2 记录。
- **部署边界**：Job Admin 地址和 access token 仅由环境变量注入，不能写入代码、规则 JSON、日志或小程序。

## 7. 动态内容生成规则

- 策略总览始终渲染四张卡：名称、原币种、活动规则版本、输入时间、状态、缺失原因、进入详情入口。
- 高分红详情显示 CNY 分红覆盖、现金垫、持仓桶和账本流水；QQQ 显示 USD 参考净值、5%/10%/12% 区间、QLD 上限与 120 日均线输入状态。
- IC/IM 详情显示 CNY 资金池、IC/IM PB 分位、贴水、保证金、压力检查、逐日结算和移仓事实；深度 Put 显示 USD 保险预算、到期梯度、保费、归零事实和缺失估值。
- 所有状态文案必须是条件说明，例如“等待数据”“观察”“接近目标”“数据过期”；不得生成下单价格、数量或自动化交易措辞。
- 交易入口预填策略上下文，但只在用户完成账本预览与确认后才将同一 `strategyKey` 写入账本事实。

### 7.1 微信小程序视觉与交互验收

- 策略总览和详情以既有 Dashboard 的移动端信息层级为内容基线，并保持已确认总览稿的暖白底色、圆角资产卡、深色主文字、低饱和状态色、底部导航和卡片间距；不得退化成默认表格或调试页。
- 总览四张策略卡必须一眼可区分固定币种、当前状态、最近输入时间与缺失原因；详情使用“概览、规则、数据与评估、账本事实”分段展示，避免把完整策略内容挤进单张静态卡。
- 历史评估以时间序列/列表展示 `asOfAt`、规则版本、状态与主要指标；没有历史数据时显示空状态，不伪造曲线。
- 页面所有金额只接收 API 十进制字符串并在界面格式化为原币种两位小数；小程序存储仅可缓存会话和展示缓存，账本、规则、参考净值与评估的唯一事实来源始终是后端 API。

## 8. 性能、安全与测试边界

### 性能与并发

- 个人首期按单管理员、单策略工作区读取设计；列表稳定游标分页，默认 20 条，最大 100 条。
- 单策略评估是本地快照计算；同输入版本以唯一键幂等收敛，批量扫描逐策略隔离失败。
- 不在请求线程执行外部行情刷新、批量历史回测或全量账本重放。

### 安全与审计

- 所有 API 都按 `owner_user_id` 隔离；规则变更限 ADMIN；请求日志不记录 access token、金额以外的敏感密钥或 XXL-JOB 配置。
- 所有金额遵循仓库金额硬规范；所有关联使用语义化 ULID，不使用外键和泛化 `biz_id`。
- `strategy_rule_version`、`strategy_evaluation`、`signal_run`、`strategy_signal`、`strategy_reference_nav` 只追加；规则切换、参考净值、评估和种子均审计来源、操作者、输入版本、创建时间。
- `strategy-test-seed` 仅在 local profile 注册，且只允许空账本；它不是生产能力。

### 数据库增量

- 对 `strategy_rule_version` 增加 `owner_user_id`，并将版本唯一约束改为 `(owner_user_id, strategy_key, rule_version)`；不以 `status=ACTIVE` 伪造唯一性。
- 新增 `strategy_db.strategy_active_rule`：自增 `id`、`strategy_active_rule_id`、`owner_user_id`、`strategy_key`、`strategy_rule_version_id`、`binding_version`、`updated_at`；无外键，唯一键为 `(owner_user_id, strategy_key)`，用于 B-1 的原子比较与切换。
- 对 `strategy_evaluation` 增加 `status`、`input_hash BINARY(32)`，并将 `input_version` 扩至 `VARCHAR(512)`；唯一键为 `(owner_user_id, strategy_rule_version_id, input_hash)`，新增 `signal_run` 状态索引。
- 对既有 `strategy_signal` 增加 `strategy_evaluation_id`、`signal_scope`、`signal_key`、`severity`、`as_of_at`；将 `signal_run_id` 与 `instrument_id` 改为可空。策略级状态用 `signal_scope=STRATEGY` 且 `instrument_id=NULL`，标的级状态才填写 `instrument_id`。删除旧唯一键，改为 `(strategy_evaluation_id, signal_key)`，使每条信号可追溯到不可变评估而不会因 NULL 唯一键语义重复。
- 新增 `strategy_db.strategy_active_rule_event`：自增 `id`、`strategy_active_rule_event_id`、`owner_user_id`、`strategy_key`、`previous_strategy_rule_version_id`（首次可空）、`next_strategy_rule_version_id`、`binding_version`、`created_by_user_id`、`created_at`；无外键，唯一键为 `strategy_active_rule_event_id`。它是活动规则指针每次变更的追加审计。
- 新增 `strategy_db.strategy_reference_nav`：自增 `id`、`strategy_reference_nav_id`、`owner_user_id`、`strategy_key`、`currency`、`reference_nav_cent`、`as_of_at`、`valid_until`、`source`、`created_at`；无外键，唯一键为 `(owner_user_id, strategy_key, currency, as_of_at)`。
- 新增 `strategy_db.strategy_seed_run`：自增 `id`、`strategy_seed_run_id`、`owner_user_id`、`seed_name`、`fixture_checksum`、`created_at`；仅记录 local 种子可追溯性。
- 对 `ledger_db.ledger_transaction` 增加可空 `strategy_key` 与 `(owner_user_id, strategy_key, occurred_on, ledger_version)` 索引；只允许四个受控策略键或 NULL，不建外键。策略页创建的交易为非空，既有非策略账本事实保持 NULL。
- 所有新表遵循三范式：规则、参考净值、评估、信号和种子运行分别建模；不复制账本交易、持仓或行情明细。

### 测试边界声明

- 本地 Unit：规则校验、币种隔离、金额规范、策略计算、幂等与状态机。
- 本地 Integration：MySQL 约束、账本/市场 port、种子、Flyway、策略读模型。
- E2E/Manual：微信小程序策略详情和账本确认闭环、XXL-JOB 注册/触发、真实本地行情刷新链路。

## 9. 测试矩阵

| # | 场景 | 规则 | 类型 | 本地可跑 | 验证层级 | 验收证据 | 预期 |
|---|---|---|---|---|---|---|---|
| 1 | 非法策略键 | P-1 | API | 是 | Unit | 422/404 断言 | 不读取任何数据。 |
| 2 | 金额键不是 `*_cent` 或为 JSON number | P-2、P-3 | API | 是 | Unit | 参数校验测试 | 拒绝规则版本。 |
| 3 | 规则币种错误 | P-4 | API | 是 | Unit | 参数校验测试 | 拒绝跨策略币种。 |
| 4 | 非法评估日期和重复扫描键 | P-5 | API | 是 | Unit | 参数校验测试 | 拒绝请求。 |
| 5 | 非 local 或非空账本调用种子 | P-6 | API | 是 | Integration | profile/账本状态断言 | 不写入种子。 |
| 6 | 乐观切换活动规则 | B-1 | Integration | 是 | Integration | MySQL 行与响应断言 | 仅新版本 ACTIVE。 |
| 7 | 评估与规则均不可变 | B-2 | Integration | 是 | Integration | 更新尝试失败、审计行存在 | 历史不被覆盖。 |
| 8 | CNY/ USD 混合输入 | B-3 | Unit | 是 | Unit | 评估 JSON 断言 | `CROSS_CURRENCY_UNVALUED`。 |
| 9 | USD 参考净值缺失/过期 | B-4、D-1 | Unit | 是 | Unit | 评估 JSON 断言 | `DATA_STALE`，无执行信号。 |
| 10 | 输入版本相同与不同 | B-5、E-4 | Integration | 是 | Integration | 并发评估与唯一键断言 | 前者复用，后者追加。 |
| 11 | 四类策略计算 | B-6 | Unit | 是 | Unit | 四套 fixture 结果断言 | 分别使用正确原币种数据。 |
| 12 | 策略交易映射 | B-7、T-3 | Integration | 是 | Integration | 账本预览/提交测试 | 无第二套流水。 |
| 13 | 状态文案不含交易指令 | B-8 | Unit | 是 | Unit | 文案快照测试 | 仅解释性结果。 |
| 14 | 完整测试种子 | B-9 | Integration | 是 | Integration | 账户、标的、23 笔交易、4 个评估 | 覆盖四类策略路径。 |
| 15 | 空工作区与缺标的/到期合约 | E-1、E-2 | Integration | 是 | Integration | API 与页面状态测试 | `BLOCKED` 且无伪造数据。 |
| 16 | 批量扫描部分成功 | E-3、D-3 | Integration | 是 | Integration | job 结果断言 | 成功策略保留，失败列表可见。 |
| 17 | XXL-JOB 刷新先后与状态迁移 | D-2、T-1、T-2 | Integration | 是 | Integration | application job 测试 | 只允许合法状态迁移。 |
| 18 | 微信小程序策略详情到账本确认 | B-7、B-8 | 启动联调 | 是 | E2E/Manual | 开发者工具截图与交易记录 | 页面预填、预览、确认闭环正确。 |
| 19 | XXL-JOB Admin 实际注册 | D-2、T-1 | 部署联调 | 否 | E2E/Manual | Job Admin 执行日志 | 不暴露密钥，任务按时执行。 |
| 20 | 期货原子移仓 | 2.1.1、B-7 | Integration | 是 | Integration | 预览与提交后两条期货事实、同一 operation group | 任一腿失败时零写入；成功时只有 `FUTURES_CLOSE` 与 `FUTURES_OPEN` 两条事实。 |
| 21 | 策略级 `DATA_STALE` 信号 | 2.2、B-4、D-1 | Integration | 是 | Integration | `strategy_signal` 行与工作区响应 | `instrument_id` 为空仍可审计追溯到评估。 |
| 22 | 小程序视觉与事实源 | 7.1 | E2E/Manual | 是 | 开发者工具 | 四策略截图、刷新后的 API 读回 | 保持移动端视觉层级；刷新后不依赖本地账本事实。 |
| 23 | 策略归属账本事实 | 2.1.2、B-7 | Integration | 是 | API + MySQL | 策略流水与未归属流水同时存在 | 评估仅读取同一 `strategy_key` 流水，修正不改变归属。 |

## 风险处置记录

- R-1：无券商 API 时误把旧行情当实时数据。**已处置**：只读本地快照；数据失效即 `DATA_STALE`。
- R-2：旧“万元”金额污染新金额规范。**已处置**：已确认换算为 CNY `*_cent`，旧单位不落库。
- R-3：USD 策略与 CNY 总资产混算。**已处置**：已确认四类策略按原币种资金池和显式 USD 参考净值计算。
- R-4：策略页面重建可变流水。**已处置**：所有交易强制复用账本预览、确认、追加/冲正模型。
- R-5：测试种子进入生产。**已处置**：仅 local profile、空账本、管理员和 fixture checksum 约束。
- R-6：活动规则与参考净值缺少独立生命周期会导致并发覆盖或版本膨胀。**已处置**：使用 `strategy_active_rule` 原子绑定和追加式 `strategy_reference_nav`，二者都不复用规则 JSON。
- R-7：旧 Dashboard 的移仓是一条浏览器记录，可能丢失两腿。**已处置**：新手工移仓强制 `FUTURES_ROLL` 原子两腿；历史不完整记录仅按已确认手续费处理。
- R-8：策略级过期或阻断状态没有标的时，旧表结构会拒绝持久化且无法审计。**已处置**：信号显式区分 `STRATEGY`/`INSTRUMENT` 范围，策略级信号允许空 `instrument_id` 并关联不可变评估。
- R-9：活动规则指针若只覆盖更新会丢失切换轨迹。**已处置**：当前指针与追加式 `strategy_active_rule_event` 分离建模。
- R-10：通用账本没有策略归属时无法准确还原高分红等任意标的策略流水。**已处置**：策略入口必须提交并持久化不可变 `strategy_key`；未归属流水不参与策略评估。

## Spec 自检

| # | 检查项 | 状态 |
|---|---|---|
| 1 | Part 3 每条规则都有 Code 映射 | ✅ |
| 2 | Part 3 每类规则都有 I/O 示例 | ✅ |
| 3 | 依赖均定义失败与部分失败策略 | ✅ |
| 4 | 测试矩阵覆盖全部规则类别与状态机 | ✅ |
| 5 | 包含空数据、非法输入、并发、部分成功和过期数据 | ✅ |
| 6 | 风险均已由用户决策并记录 | ✅ |
| 7 | 无未决 TBD | ✅ |
| 8 | 区分本地、集成与真实链路验收 | ✅ |
| 9 | 时序与状态机及其测试均已定义 | ✅ |
| 10 | 无未确认的外部券商/行情依赖；XXL-JOB 仅作为调度入口 | ✅ |
| 11 | 首版规则、策略级信号、原子移仓与种子交易数均有唯一可执行契约 | ✅ |
| 12 | 策略归属由账本事实而非标的命名或前端缓存决定 | ✅ |
