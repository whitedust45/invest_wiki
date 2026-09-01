# Phase 2：个人投资账本、持仓、估值与导入可执行规格

**状态：** 已批准，按本规格进入 TDD 实现与验收。

**依据：** [总体实施规划](wechat-miniprogram-java-ddd-implementation-plan.md)、[Phase 1 规格](phase1-foundation-spec.md)、[最终语义 Schema](mysql-ddd-schema-v1.sql)、根目录 `AGENTS.md` 的金额与业务 ID 硬规范。

**已确认决策：**

- 首批同时实现现金、股票/ETF、分红/利息、费用、内部转账、IC/IM 多头期货、买入型期权、手工估值和 SQLite/JSON 历史导入。
- 历史数据均为测试数据；不需要迁移或保留当前旧系统的数据，但导入功能必须可用。一个导入批次只能选择一份 JSON 完整备份或一个明确的 SQLite 快照，禁止合并多个全量快照。
- 用户可创建多个现金账户；系统科目不由前端选择。CNY 现金账户在首次期货操作时自动创建同币种配套的可用/锁定保证金账户。
- 新标的可在交易表单内创建，唯一键为 `(market, exchange, symbol)`。
- 分红/利息录入税前总额；代扣税可选，未填视为零；收入按税前额确认，现金按税后额到账。
- 期货仅支持 IC/IM 多头开仓、平仓、保证金划转和移仓；不支持空头、逐日盯市自动结算。
- 期货首期必须支持**手工逐日盯市结算**；自动行情触发结算留待后续市场数据与 XXL-JOB 切片，不得把未结算损益伪装为券商现金余额。
- 期权仅支持买入开仓、卖出平仓，以及用户明确确认“无价值”的到期核销；不支持卖空、行权和指派。实值或可能有行权价值的期权不得直接归零。
- 股票、ETF、期权数量为正数、最多 8 位小数；期货手数为正整数。
- 所有业务货币金额均以原币种两位小数最小单位的 `*_cent` 十进制字符串传输、`BIGINT` 存储；USD 不换汇、不折算为 CNY；所有汇总按币种隔离。
- 股票/ETF 首期支持拆股、并股、送股三类非现金公司行为；合并、分拆、配股、退市与现金代替零股明确拒绝并标识为 `CORPORATE_ACTION_UNSUPPORTED`，绝不猜测数量或成本。
- 允许补录历史日期与冲正/替代，但必须在同一事务锁内从最早受影响业务日期确定性重放该 owner 的全部投影；重放任一中间状态不成立则整笔拒绝。
- 买入手续费资本化进入 FIFO 批次成本；卖出手续费单独记录为交易费用并从报表的已实现损益扣减。代扣税使用独立税费科目。
- 首期及可预见后续阶段不接入券商 API；仅提供手工对账，差异进入 `NEEDS_REVIEW`，不得自动调账。
- 体验版与正式版必须使用真实微信登录、HTTPS 合法域名、隐私与发布资料门禁；local Mock、手填登录 code 和本地 HTTP 只允许 local profile。

## 1. 接口背景与功能

**背景：** Phase 1 已完成身份、会话和数据库基础。Phase 2 将旧浏览器本地状态替换为可审计的复式分录事实库，并将持仓、批次、估值和导入实现为可重放读模型。

**功能：** 管理现金账户和标的；追加不可变的账务交易及分录；以 FIFO 重放现货/期权批次与受支持的公司行为；维护期货多头、手工逐日结算与保证金；维护手工估值与手工对账；安全导入单份 JSON/SQLite 账本快照。

**使用者：** 已认证的唯一 `ADMIN`；微信小程序账本、总览与导入页面；本地自动化测试。

**交互模式：** 小程序通过 `/api/v1` REST API 读写；同步账务写入在一个 MySQL 本地事务中完成；本地实现把导入 dry-run 同步执行但持久化为可轮询 job，生产异步 worker 为部署扩展项；每次 dry-run 在独立 rollback-only 事务中演练同一套账本追加命令；小程序使用 `wx.chooseMessageFile` 与 `wx.uploadFile` 通过 HTTPS 预签名 POST 上传；对象存储仅保存导入原件与对账证据。

**不包含：** 行情自动抓取、XXL-JOB 自动结算、券商 API/券商下单、支付、FX 换算、期货空头、期权卖空/行权/指派、合并/分拆/配股/退市等未支持公司行为、MQ/RPC/TCC。真实微信登录与云发布可不阻断 local 编码，但作为体验版与正式版的强制发布门禁。

## 2. 接口定义

### 2.1 公共约定

- 所有接口都要求 Phase 1 的 Bearer 会话和 `ADMIN` 角色；服务端从会话取得 `owner_user_id`，请求不得携带可信用户 ID。
- 账户创建/停用、标的创建、交易创建、冲正/替代、手工估值、手工对账、上传请求、导入 job 创建和导入确认均必须携带 `Idempotency-Key`（1 至 128 字符）。同键同规范请求返回首次响应；同键不同请求体返回 `409 IDEMPOTENCY_KEY_REUSED`。
- 所有写响应只暴露语义化 ULID，不暴露自增 `id`。跨实体关系使用 `account_id`、`transaction_id`、`instrument_id` 等语义 ID，绝不使用外键或 `biz_id`。
- 金额 JSON 一律使用如 `amount_cent`、`unit_price_cent`、`fee_cent`、`tax_withheld_cent`、`market_value_cent` 的十进制字符串；禁止 JSON number、`float`、`double`、`decimal` 或“万元”。
- 数量 JSON 是十进制字符串；小程序显示金额时只格式化字符串，不参与浮点运算。
- 成功写响应为 `201` 或 `202`；错误为 RFC 7807 风格 `{code,message,traceId,details}`。
- 体验版与正式版会话采用 30 分钟无操作失效、8 小时绝对失效；登出、管理员 allowlist/权限变更和密钥轮换立即使该用户全部会话失效。local profile 的临时会话参数不得进入发布配置。
- 登录、上传凭据、交易、导入确认和对账写入按 IP、openid HMAC、会话三层服务端限流；拒绝返回 `429 RATE_LIMITED`，小程序不得自动重放高风险写入。

### 2.2 账户与标的

```yaml
POST /api/v1/ledger/accounts
request:
  displayName: string              # 1..128，用户可读名称
  currency: string                 # ISO 4217 大写，首期 CNY 或 USD
response: { accountId, displayName, accountKind: ASSET_CASH, currency, version }

GET /api/v1/ledger/accounts
response: { items: [CashAccount] }

PATCH /api/v1/ledger/accounts/{accountId}
headers: { If-Match: account-version }
request: { status: DISABLED }
response: { accountId, status: DISABLED, version }

POST /api/v1/market/instruments
request:
  market: string                   # 如 CN / US / CFFEX
  exchange: string                 # 如 SSE / NASDAQ / CFFEX
  symbol: string                   # 1..64，规范化后唯一
  displayName: string              # 1..256
  assetType: EQUITY|ETF|FUTURE|OPTION
  nativeCurrency: CNY|USD
  maturityDate: date|null          # FUTURE/OPTION 必填
  future:                           # 仅 FUTURE 必填
    productCode: IC|IM
    contract_multiplier_cent: string
  option:                           # 仅 OPTION 必填
    underlyingInstrumentId: ULID
    optionRight: PUT|CALL
    strike_price_cent: string
    contract_multiplier: string      # 非货币正整数；每张合约对应的标的单位数
response: Instrument
```

创建标的接口若 `(market,exchange,symbol)` 已存在且字段完全一致，返回既有标的；冲突字段返回 `409 INSTRUMENT_CONFLICT`。`FUTURE` 只能提交 `future`、`OPTION` 只能提交 `option`；其他资产类型两者均为 `null`。期货乘数是 `future_contract` 中随合约创建、不可由后续交易覆盖的原币种“每点每手金额”；Ledger 从该主数据复制乘数和 `maturityDate` 到交易明细快照。期权合约乘数是**非货币整数**，`unit_price_cent` 表示每标的单位的权利金；期权的标的必须是同币种 `EQUITY`/`ETF`。期权到期日的唯一权威来源是 `instrument.maturity_date`，`option_contract` 不重复存储。小程序“交易表单内新建标的”的交互必须先调用该 Market Data 接口、取得 `instrumentId`，再调用 Ledger 接口；Ledger 请求不接收内联标的，从而保持 Ledger 不依赖 Market Data 写模型。

### 2.3 账务交易

```yaml
POST /api/v1/ledger/transactions
request:
  transactionType: EXTERNAL_FUNDING|EXTERNAL_WITHDRAWAL|INTERNAL_TRANSFER|TRADE_BUY|TRADE_SELL|DIVIDEND|INTEREST|FEE|FUTURES_OPEN|FUTURES_CLOSE|FUTURES_MARGIN|FUTURES_DAILY_SETTLEMENT|FUTURES_ROLL|OPTION_OPEN|OPTION_CLOSE|OPTION_EXPIRE|CORPORATE_ACTION
  occurredOn: date
  note: string|null                 # 最大 1000；原文不写入结构化日志
  cashAccountId: ULID|null
  destinationAccountId: ULID|null   # 仅 INTERNAL_TRANSFER
  instrumentId: ULID|null
  quantity: string|null
  unit_price_cent: string|null      # 现货/期权单价
  pricePoints: string|null          # 期货点位
  initial_margin_cent: string|null   # FUTURES_OPEN 必填
  fee_cent: string|null               # 可选，未填为 0
  amount_cent: string|null            # 资金、分红、利息、独立费用、划转金额
  tax_withheld_cent: string|null      # DIVIDEND/INTEREST 可选，未填为 0
  entitlementDate: date|null          # 仅 DIVIDEND；权益登记/除权口径日期
  per_share_amount_cent: string|null  # 仅 DIVIDEND 可选；每股/份税前金额
  marginDirection: IN|OUT|null        # 仅 FUTURES_MARGIN 必填
  settlementPricePoints: string|null  # 仅 FUTURES_DAILY_SETTLEMENT 必填
  expiryOutcome: WORTHLESS|null       # 仅 OPTION_EXPIRE 必填，操作者确认无价值
  corporateAction:                    # 仅 CORPORATE_ACTION 必填；其余类型必须为 null
    actionType: STOCK_SPLIT|REVERSE_SPLIT|STOCK_DIVIDEND
    instrumentId: ULID
    ratioNumerator: string             # 正整数
    ratioDenominator: string           # 正整数
  roll:                              # 仅 FUTURES_ROLL 必填；其余类型必须为 null
    close:
      instrumentId: ULID
      quantity: string
      pricePoints: string
    open:
      instrumentId: ULID
      quantity: string
      pricePoints: string
      initial_margin_cent: string
response:
  transactionId: ULID
  transactionType: string
  occurredOn: date
  currency: string
  postings: [{ postingId, accountId, postingSide, amount_cent, currency }]
  tradeDetails: [{ tradeDetailId, instrumentId, positionEffect, quantity }]
  ledgerVersion: string
  operationGroupKey: ULID|null
  relatedTransactionIds: [ULID]     # FUTURES_ROLL 返回平仓、开仓两个 transaction_id
```

公开 `transactionType` 不接受内部 `REVERSAL`。数据库实际存储的交易类型为所有公开的单腿类型加 `REVERSAL`；`FUTURES_ROLL` 是命令类型而非持久化交易类型，成功时原子写入一条 `FUTURES_CLOSE` 和一条 `FUTURES_OPEN`，两者共享 `operation_group_key`。普通手工交易的 `source_type=MANUAL`，导入为 `IMPORT`，冲正为 `CORRECTION_REVERSAL`，更正中的替代交易为 `CORRECTION_REPLACEMENT`。替代交易仍保存其提交的正常业务类型，绝不以虚假的 `REPLACEMENT` 类型入账。

```yaml
POST /api/v1/ledger/transactions/preview
request: TransactionCreate            # 与创建交易相同，但没有 Idempotency-Key
response:
  draftHash: sha256-hex
  currency: string
  postings: [{ accountCode, displayName, postingSide, amount_cent, currency }]
  tradeDetails: [{ instrumentId, positionEffect, quantity }]
  accountProvisioning: [AccountProvisioning]
  validationWarnings: [string]
```

```yaml
POST /api/v1/ledger/transactions/{transactionId}/corrections
request:
  replacement: TransactionCreate|null # null=仅冲正；非空=冲正后追加替代交易
response:
  reversalTransactionIds: [ULID]       # 普通交易 1 条；FUTURES_ROLL 原子返回 2 条
  replacementTransactionIds: [ULID]    # 无替代时为空；FUTURES_ROLL 原子返回 2 条
  correctionRootTransactionIds: [ULID] # 与受影响原交易一一对应
  ledgerVersion: string

GET /api/v1/ledger/transactions?cursor=&limit=&accountId=&instrumentId=&from=&to=
response:
  items: [LedgerTransactionView]
  nextCursor: string|null
```

`limit` 为 1..100，默认 30。只读视图包含交易、分录、明细、冲正链和 `asOf`；不返回系统密钥、原始导入文件内容或物理主键。

`FUTURES_ROLL` 的成功响应以 `transactionId` 表示平仓腿，并通过 `relatedTransactionIds` 返回平仓、开仓两个交易 ID；两个事实必须共享 `operationGroupKey`，且同时存在或同时不存在。对任一腿发起更正时，服务端必须解析整个 operation group，并原子冲正两腿；若提交替代，替代必须也是完整的 `FUTURES_ROLL`，不得只替换其中一腿。

交易预览不得写数据库、不得生成业务 ID、不得占用幂等键；它在同一账本版本快照上执行与实际命令相同的余额、FIFO、公司行为、结算和历史重放模拟。实际提交必须重新校验并以 `Idempotency-Key` 原子落库，预览不构成提交承诺。

| `transactionType` | 必填业务字段 | 额外限制 |
| --- | --- | --- |
| `EXTERNAL_FUNDING` / `EXTERNAL_WITHDRAWAL` | `cashAccountId`、`amount_cent` | 不得带标的、数量、价格、费用。 |
| `INTERNAL_TRANSFER` | `cashAccountId`、`destinationAccountId`、`amount_cent` | 源/目标必须是不同的用户现金账户且同币种；现金与保证金之间的移动只能使用 `FUTURES_MARGIN`。 |
| `TRADE_BUY` / `TRADE_SELL` | `cashAccountId`、`instrumentId`、`quantity`、`unit_price_cent` | 仅 `EQUITY`/`ETF`；`fee_cent` 可选。 |
| `DIVIDEND` | `cashAccountId`、`instrumentId`、`amount_cent`、`entitlementDate` | `tax_withheld_cent`、`per_share_amount_cent` 可选且税额不大于税前额；提供每股金额时必须通过权益日持仓的精确核验。 |
| `INTEREST` / `FEE` | `cashAccountId`、`amount_cent` | 利息标的可空；独立费用不得带数量/价格。 |
| `FUTURES_OPEN` | `cashAccountId`、`instrumentId`、`quantity`、`pricePoints`、`initial_margin_cent` | 仅完整 `future_contract` 主数据的 CFFEX IC/IM 多头，整手，交割日必须存在；`occurredOn` 必须早于交割日；乘数服务端读取并快照。 |
| `FUTURES_CLOSE` | `cashAccountId`、`instrumentId`、`quantity`、`pricePoints` | 仅平同现金账户配套保证金账户中的现有同合约多头；`occurredOn` 不得晚于交割日。 |
| `FUTURES_MARGIN` | `cashAccountId`、`amount_cent` | 只能在该现金账户与配套“可用保证金”账户间划转；方向由 `marginDirection: IN|OUT` 指定。 |
| `FUTURES_DAILY_SETTLEMENT` | `cashAccountId`、`instrumentId`、`settlementPricePoints` | 仅手工录入；结算日严格晚于该账户该合约的上次结算/开仓基准日，且不得晚于交割日。 |
| `FUTURES_ROLL` | `cashAccountId`、`roll.close`、`roll.open` | 两腿同币种、同日、同一 operation group；旧腿必须有未平多头，旧腿日期不得晚于交割日、新腿日期必须早于新合约交割日。 |
| `OPTION_OPEN` / `OPTION_CLOSE` | `cashAccountId`、`instrumentId`、`quantity`、`unit_price_cent` | 仅完整元数据的多头 PUT/CALL；总权利金为 `quantity × contract_multiplier × unit_price_cent`，必须精确为整数分；服务端必须把非货币 `contract_multiplier` 快照写入交易明细；关闭不得超过 FIFO 批次。 |
| `OPTION_EXPIRE` | `cashAccountId`、`instrumentId`、`quantity`、`expiryOutcome: WORTHLESS` | 仅未平多头批次；`occurredOn` 必须等于合约到期日，`quantity` 必须一次性等于该现金账户/合约的全部剩余数量；该确认表示无行权价值，不得用于实值或不确定价值期权。 |
| `CORPORATE_ACTION` | `corporateAction` | 顶层 `instrumentId` 必须为 `null`，仅使用 `corporateAction.instrumentId`；仅 `EQUITY`/`ETF` 的拆股、并股、送股；不带现金账户、金额、价格、费用或税额。`occurredOn` 即公司行为生效日，数量变换后每个批次必须仍在 8 位小数内。 |

### 2.4 持仓、估值与导入

```yaml
GET /api/v1/portfolio/summary
response: { items: [{ currency, cash_cent, margin_cent, market_value_cent|null, net_asset_cent|null, positions, asOf, sourceLedgerVersion, valuationStatus }] }

GET /api/v1/portfolio/positions?currency=CNY|USD&accountId=&asOf=
response: { currency, cash_cent, margin_cent, market_value_cent|null, net_asset_cent|null, positions, asOf, sourceLedgerVersion, valuationStatus }

POST /api/v1/portfolio/manual-valuations
request:
  instrumentId: ULID
  valuationDate: date
  currency: CNY|USD
  unit_price_cent: string|null
  market_value_cent: string|null
  validUntil: date-time|null
  note: string|null
response:
  manualValuationId: ULID
  instrumentId: ULID
  valuationDate: date
  unit_price_cent: string|null
  market_value_cent: string|null
  currency: CNY|USD
  priority: 100
  validUntil: date-time|null
  valuationStatus: ACTIVE|EXPIRED

POST /api/v1/files/upload-requests
request: { direction: IMPORT|RECONCILIATION_EVIDENCE, mediaType: application/json|application/x-sqlite3|application/pdf|image/jpeg|image/png, byteSize: string, sha256: hex }
response: { importExportFileId, uploadUrl, method: POST, fileField: file, formData, expiresAt }

POST /api/v1/ledger/imports
request:
  importExportFileId: ULID
  format: LEGACY_DASHBOARD_JSON|LEGACY_SQLITE
  snapshotId: string|null             # LEGACY_SQLITE 必填；十进制 SQLite snapshots.id
  currencyMappings:
    - selector: { module: string, action: string|null }
      currency: CNY|USD
      amountUnit: LEGACY_CNY_WAN|ORIGINAL_CURRENCY_DECIMAL
      cashAccountId: ULID
  instrumentMappings:
    - selector: { module: string, symbol: string }
      instrumentId: ULID
  dividendEntitlementOverrides:
    - sourceRow: integer               # entries 数组 1 起始行号
      entitlementDate: date
  optionExpiryAttestations:
    - sourceRow: integer               # entries 数组 1 起始行号
      expiryOutcome: WORTHLESS
response: { jobId, status: PENDING }

GET /api/v1/jobs/{jobId}
response: { jobId, status, preview, error }

POST /api/v1/ledger/imports/{jobId}/confirm
request: { expectedChecksum: hex }
response: { importedTransactionCount, rejectedRowCount: 0, ledgerVersion }

POST /api/v1/portfolio/reconciliations
request:
  cashAccountId: ULID
  reconciliationDate: date
  broker_cash_cent: string
  positions: [{ instrumentId: ULID, quantity: string }]
  attachmentImportExportFileId: ULID|null
  discrepancyReason: string|null
response: { reconciliationId, status: MATCHED|NEEDS_REVIEW, cash_difference_cent, cashDifferenceDirection: NONE|BROKER_GREATER|LEDGER_GREATER, positionDifferences, sourceLedgerVersion }

GET /api/v1/portfolio/reconciliations?cashAccountId=&from=&to=&cursor=&limit=
response: { items: [ReconciliationView], nextCursor: string|null }
```

`POST manual-valuations` 只能提供 `unit_price_cent` 或 `market_value_cent` 之一，不能同时为空或同时有值；服务端固定 `priority=100`、`source=MANUAL`，每次 POST 追加一条估值事实，不原地覆盖历史。只接受 `EQUITY`、`ETF`、`OPTION`，期货必须通过手工逐日结算确认盈亏。读取时只选择估值日不晚于 `asOf`、且未过期的手工估值，按 `priority DESC, valuation_date DESC, created_at DESC` 决胜；它优先于市场价。导入确认只接受 `SUCCEEDED` dry-run job，且 checksum 必须等于预览值。

`GET /portfolio/reconciliations` 的 `limit` 为 1..100、默认 30；结果仅返回当前 owner 的对账事实，按 `reconciliation_date DESC, source_ledger_version DESC, created_at DESC` 排序。

对账只接受操作者手工录入的券商现金与标的数量，不调用、抓取或模拟任何券商 API。`positions` 是该账户/日期所有非零券商持仓的完整快照，标的不得重复；服务端以账本持仓与请求持仓的并集计算差异。完全一致为 `MATCHED`；任一现金或数量差异为 `NEEDS_REVIEW`，此时 `discrepancyReason` 必填。附件只是证据，不能触发自动更正或自动生成账务分录。

`LEGACY_DASHBOARD_JSON` 只接受完整导出中的 `ledger.entries` 数组；`LEGACY_SQLITE` 只接受白名单 `snapshots(id, created_at, payload_json)` 表中、由 `snapshotId` 选定的一条 payload，其根为 `entries` 数组。`sourceRow` 固定为该 `entries` 数组的 1 起始位置，服务端把 override/attestation 与上传对象的 SHA-256、`snapshotId` 一并写入 preview checksum；文件或快照改变后它们全部失效。每一个旧行必须且只能命中一个 `currencyMappings.selector`，其中 `cashAccountId` 必须属于当前 owner、处于 `ACTIVE` 且币种与映射一致；每个需要标的的旧行必须且只能命中一个以规范化 `(module,symbol)` 定位的 `instrumentMappings.selector`，且目标标的必须存在并与动作兼容。导入不会隐式创建现金账户或标的：缺失标的须先通过 Market 接口创建，再重新发起 dry-run。未命中、命中多个、单位不匹配、必需权益日 override/无价值 attestation 缺失，或金额无法无舍入地转为 `*_cent` 时 dry-run 拒绝。`LEGACY_CNY_WAN` 以原始十进制文本乘 `1,000,000`；`ORIGINAL_CURRENCY_DECIMAL` 以原始十进制文本乘 `100`，均使用精确十进制运算且不允许舍入。

## 3. 业务规则与边界条件

### 3.1 参数校验

- P-1：所有 `*_cent` 必须是 `long` 范围内的十进制整数字符串。必填金额、单价、保证金、行权价和合约乘数必须大于零；可选 `fee_cent`、`tax_withheld_cent` 可省略（按零处理）或显式为零，其余已提交金额不得为零或负数 → `400 MONEY_FORMAT_INVALID`。
- P-2：币种不是大写 `CNY`/`USD`、账户币种与标的/金额币种不同、或任何写操作试图隐式换汇 → `422 CURRENCY_MISMATCH`。
- P-3：数量或 `pricePoints` 非正、超过 8 位小数，期货数量不是整数，或期权 `contract_multiplier` 不是正整数 → `400 QUANTITY_INVALID`。
- P-4：交易类型缺少它要求的现金账户、标的、金额、价格、保证金、交割日、结算价、公司行为或期权合约资料 → `400 TRANSACTION_FIELDS_INVALID`。
- P-5：标的唯一键已存在但提交字段与现有事实冲突 → `409 INSTRUMENT_CONFLICT`。
- P-6：账户名称空白/超长、币种不支持、Idempotency-Key 缺失/超长、分页或上传元数据越界 → `400 REQUEST_VALIDATION_FAILED`。
- P-7：导入文件类型、大小、SHA-256、SQLite `snapshots` 结构、JSON `entries` 结构、指定快照、每一行货币映射、所需分红权益日 override 或期权无价值 attestation 无效/缺失 → `422 IMPORT_VALIDATION_FAILED`。

### 3.2 核心业务规则

- B-1：用户创建的账户只能是同币种 `ASSET_CASH`；同用户、同显示名及币种不可重复。任何需要现金账户的交易只能选择其本人处于 `ACTIVE` 状态的现金账户；无现金的 `CORPORATE_ACTION` 不得伪造账户。账户停用必须携带 `If-Match` 的当前版本，且该账户现金余额为零、没有现货/期权/期货未平仓、没有锁定保证金、没有引用它的 `PENDING`/`RUNNING` 导入 job。手工对账是同一事务完成的同步命令，不存在可遗留的 pending 对账 job；任一对账写入失败即整笔回滚。成功停用后递增版本。不得物理删除、不得修改币种和既有 account code。
- B-2：系统科目不能由请求指定，且以稳定 `account_code` 唯一定位：`SYS:{kind}:{currency}` 用于 `EQUITY_EXTERNAL`、收入、费用和 PnL；`MRGAV:{cash_account_id}` 和 `MRGLK:{cash_account_id}` 分别是配套保证金的可用/锁定账户（均为 `ASSET_MARGIN`）；`INV:{cash_account_id}:{instrument_id}` 用于该现金账户内该标的的投资成本。本切片不创建未使用的 `ASSET_CLEARING` 科目。所有代码长度小于 64；首次期货命令在同一事务中创建两类保证金账户。
- B-3：除 `CORPORATE_ACTION` 和“结算盈亏恰为零”的 `FUTURES_DAILY_SETTLEMENT` 外，每个 `LedgerTransaction` 在同一 MySQL 事务中追加至少两条不可变 `ledger_posting`。按 `currency` 分组后，`DEBIT` 与 `CREDIT` 的 `amount_cent` 总额必须相等；前两类非货币/零金额事实只追加对应的不可变业务明细，不得伪造零金额或自对冲分录。失败时整笔写入、幂等记录、审计和投影均回滚。
- B-4：`EXTERNAL_FUNDING` 借现金贷外部权益；`EXTERNAL_WITHDRAWAL` 借外部权益贷现金；`INTERNAL_TRANSFER` 借目标用户现金账户贷源用户现金账户，源和目标必须同币种。系统保证金账户不接受该请求；现金与保证金之间只能由 `FUTURES_MARGIN` 按 B-10 划转。
- B-5：现货/ETF `TRADE_BUY` 的 `gross_cost_cent = quantity × unit_price_cent` 必须精确为整数分；买入手续费资本化，借该账户-标的的投资成本账户 `gross_cost_cent + fee_cent`、贷现金相同金额，并把该全额作为 FIFO 批次的 `opened_cost_cent`。`TRADE_SELL` 按 FIFO 精确分配 `remaining_cost_cent`：先借现金 `gross_proceeds_cent - fee_cent`、借 `EXPENSE_FEE` `fee_cent`、贷投资成本 `allocated_cost_cent`；若 `gross_proceeds_cent >= allocated_cost_cent`，再贷 `PNL_REALIZED` 差额，否则借 `PNL_REALIZED` 差额。报表的 `net_realized_pnl_cent = gross_proceeds_cent - allocated_cost_cent - fee_cent`；金额方向只由 `posting_side` 表达，不得以负金额、平均成本或舍入替代。
- B-6：`DIVIDEND`/`INTEREST` 的 `amount_cent` 为税前收入；借现金 `amount_cent - tax_withheld_cent`，若税额大于零再借 `EXPENSE_WITHHOLDING_TAX` `tax_withheld_cent`，贷收入 `amount_cent`，并追加唯一不可变 `ledger_income_detail`。分红必须带标的和 `entitlementDate`，利息可不带标的；若提供 `per_share_amount_cent`，服务端必须以权益日可用持仓精确计算税前额。历史导入缺失权益日或无法核验的分红行进入 `NEEDS_REVIEW`，不得猜测。
- B-7：独立 `FEE` 借费用贷现金。普通交易可附加 `fee_cent`，但不得同时提交一笔同语义的重复 FEE。
- B-8：现货、ETF、期权持仓使用 FIFO，按 `(occurred_on, transaction_id, detail_no)` 消费未平批次；所有成本分配以 `remaining_cost_cent` 为唯一精确真相，`unit_cost_cent`/平均成本仅可用于展示且不得驱动分录。卖出/期权平仓数量超过可用批次 → 拒绝；不允许移动加权平均、负持仓或跨币种批次。`CORPORATE_ACTION` 生效日必须至少有一个该 owner/标的的未平现货/ETF 批次，并按生效日对全部此类批次数量应用 `ratio_numerator / ratio_denominator`，总 `remaining_cost_cent` 保持不变；任一变换结果超出 8 位小数、没有可变换批次或要求现金代替零股即拒绝。
- B-9：`FUTURES_OPEN` 仅允许具备完整、有效 `future_contract` 主数据的 IC/IM 多头；服务端从该主数据取得乘数、从 `instrument.maturity_date` 取得交割日并快照到 `ledger_trade_detail`，请求中的任何自报乘数均拒绝。由于本切片不接券商 API、无实物交割能力，开仓业务日必须早于交割日，平仓、手工结算和移仓旧腿不得晚于交割日；任一读取或命令的 `asOf`/`occurredOn` 晚于交割日且该合约仍有未平仓时，返回 `422 FUTURES_MATURITY_ACTION_REQUIRED`，不得自动结算、自动平仓或让过期合约继续进入投影。写入正整数手数和点位后，锁定初始保证金的分录必须为**借 `MRGLK`、贷 `MRGAV`**，并在期货批次保存 `allocated_initial_margin_cent`、`remaining_initial_margin_cent` 与 `last_settlement_price_points`（开仓时等于开仓点位）；可选费用按借 `EXPENSE_FEE`、贷用户现金账户入账，不得从保证金锁定额中隐式扣除。`FUTURES_DAILY_SETTLEMENT` 是唯一允许的手工逐日结算：它必须追加一条 `position_effect=NONE` 的 `ledger_trade_detail`，记录已结算总手数和 `settlementPricePoints`；再对该现金账户/合约全部未平批次计算 `(settlement_price_points - last_settlement_price_points) × contract_multiplier_cent × remaining_quantity`，结果必须精确为整数分。盈亏非零时盈利借 `MRGAV` 贷 `PNL_REALIZED`、亏损反向分录；盈亏为零时不写伪造分录，只保留结算明细与审计。无论盈亏，各批次结算基准均更新为该结算价。结算日必须单调递增且不得自动生成。`FUTURES_CLOSE` 仅能按 FIFO 平已有同合约多头批次；对每个被消费批次按 `closed_quantity / opened_quantity` 精确分配保证金，最后一笔消费该批次时承接全部余数。平仓先借 `MRGAV`、贷 `MRGLK` 释放锁定保证金；其盈亏以 `close_price_points - last_settlement_price_points` 计算并按同一规则入账。资金留在保证金双账户内，只有 `FUTURES_MARGIN OUT` 可转回现金。不允许空头开仓或平仓超过持仓。
- B-10：`FUTURES_MARGIN` 仅在现金账户与其配套 `MRGAV` 账户间划转：`IN` 为借 `MRGAV`、贷现金，`OUT` 为借现金、贷 `MRGAV`；`OUT` 不得动用 `MRGLK` 锁定余额。`FUTURES_ROLL` 在一个 `operation_group_key` 下原子追加同日平旧合约和开新合约事实，不能通过修改原合约实现。移仓平仓腿的盈亏同样相对每批次 `last_settlement_price_points` 结算。
- B-11：`OPTION_OPEN`、`OPTION_CLOSE`、`OPTION_EXPIRE` 仅对应多头期权。开仓/平仓业务日不得晚于到期日。期权权利金总额为 `quantity × contract_multiplier × unit_price_cent`，必须精确为整数分；开仓手续费与权利金一并资本化为 FIFO 成本，平仓手续费按 B-5 独立计入 `EXPENSE_FEE` 并计入净已实现损益。开仓/平仓均把创建时的非货币 `contract_multiplier` 快照到 `ledger_trade_detail.option_contract_multiplier`，重放不得读取可变的 Market 查询结果。`OPTION_EXPIRE` 的业务日必须等于到期日、数量必须覆盖该现金账户/合约全部未平 FIFO 批次，且必须由认证操作者提交 `expiryOutcome=WORTHLESS`，表示确认无行权价值；其分录为借 `EXPENSE_OPTION`、贷剩余投资成本。卖空、行权、指派、对实值/价值不确定期权的归零，以及没有完整 PUT/CALL、同币种 `EQUITY`/`ETF` 标的、行权价、到期日、非货币整数乘数的期权标的一律拒绝。
- B-12：交易、分录、交易明细与公司行为不得更新或删除。普通交易的更正接口先追加内部类型 `REVERSAL`（逐条镜像货币分录，并追加抵消原交易持仓效果的交易明细），可选再追加保存为正常业务类型的替代交易；冲正的业务日期固定为原交易 `occurredOn`，替代交易使用其提交的业务日期，二者的 `created_at` 保留真实审计时间。每个根交易的原始事实固定 `revision_no=0`；其直接冲正使用下一个未占用版本号，若有替代交易再使用紧随的下一个版本号，从而严格满足 `(correction_root_transaction_id, revision_no)` 唯一。替代交易仍可在以后作为新的更正目标并沿用同一 root 继续分配版本；`REVERSAL` 本身和已被直接冲正的目标不得重复冲正。若目标属于 `FUTURES_ROLL` operation group，必须一次性冲正两腿；可选替代也必须原子写入完整的新移仓两腿。公司行为冲正使用倒数比例，期货逐日结算冲正恢复该批次结算前基准并镜像原结算损益；两者均必须通过 8 位数量与精确分校验。每一物理交易的 `correction_root_transaction_id` 固定首笔。若对原交易、冲正和替代按业务日期重放后任一时点出现现金、保证金、持仓、期权、期货结算或公司行为不变量不成立，则拒绝整个更正命令，不留部分事实。
- B-13：每个成功命令先以唯一 `owner_user_id` 原子创建缺失的 `ledger_state`，再以 `SELECT ... FOR UPDATE` 锁定该行，按命令产生的物理交易数分配连续 `ledger_version`；再持久化幂等记录、账务事实、审计、outbox 事件和投影版本，最后更新 `ledger_state.ledger_version` 并将幂等状态置为 `SUCCEEDED` 后提交。`FUTURES_ROLL` 两条事实占用两个连续版本，响应返回最高版本。对历史补录、冲正或替代，锁内先确定最早受影响 `occurredOn`，随后按 `(occurred_on, transaction_id, detail_no)` 全量重放该 owner 的投影；重放和账务写入同一事务提交。相同键相同请求返回首次响应；相同键不同请求返回冲突。
- B-14：每条 `ledger_transaction` 持久化其 `ledger_version`；持仓、现货批次、期货批次、每日汇总均是 Ledger 的单向可重放投影，并将命令最高账本版本写为 `source_ledger_version`。投影不得回写或修改账务事实；历史补录、公司行为与更正后的旧 `asOf` 快照按最新账本版本重算并标明已重述。
- B-15：手工估值只覆盖读模型，不能改写成本、流水或批次；估值币种必须与标的原生币种相同。服务端固定 `priority=100`、`source=MANUAL`，在 `valuation_date <= asOf` 的候选中按 `priority DESC, valuation_date DESC, created_at DESC` 选择未过期记录。`unit_price_cent` 按账户计算：股票/ETF 为 `quantity × unit_price_cent`，期权额外乘不可变合约乘数，任一结果不能精确为整数分即显示 `PRECISION_UNAVAILABLE`，绝不舍入。`market_value_cent` 的语义固定为“用户 + 标的”的总市值，只在 `GET /portfolio/summary` 中按标的计入一次；`GET /portfolio/positions` 与任何账户视图都返回 `MANUAL_TOTAL_UNALLOCATED`，不得擅自拆分。期货只显示现金、可用/锁定保证金与已手工确认的逐日结算盈亏，禁止手工市值或名义价值，以免与保证金重复计算。若同一原币种有任一未估值、已过期、精度不足或未逐日结算的期货持仓，`market_value_cent`、`net_asset_cent` 必须返回 `null` 并给出显式状态；无行情时显示 `MANUAL`，无可用估值时显示 `UNVALUED`；任何跨币种合计显示 `CROSS_CURRENCY_UNVALUED` 而非换算。
- B-15a：手工对账是独立、不可变的 Portfolio 事实。它比较给定账户/日期的重放现金与持仓数量同操作者录入的券商数据；请求中的每个标的原币种必须等于该现金账户币种，跨币种券商资产必须拆分到对应币种现金账户后分别对账。`portfolio_reconciliation_position` 必须持久化“券商非零持仓 ∪ 账本非零持仓”的完整并集快照，包含数量相等的行，`quantity_difference = broker_quantity - ledger_quantity` 可为有符号非货币数量。完全一致才为 `MATCHED`，否则为 `NEEDS_REVIEW`。同一账户/日期可在新账本版本或修正人工输入后追加新的对账事实，读取时按 `source_ledger_version DESC, created_at DESC` 选择最新一条。对账证据仅保存隔离对象键与 SHA-256，不得自动创建现金、交易、估值或调整分录。
- B-16：导入必须经过“上传原件 SHA-256 → MIME/大小/结构白名单 → 单一快照选择 → 显式 `currencyMappings`（含目标现金账户）、`instrumentMappings`、金额单位映射、分红权益日 override 和期权无价值 attestation → dry-run 动作映射/分录平衡/FIFO 检查 → 返回预览 checksum → 管理员确认 → 单事务提交 → 重放投影”。导入不创建现金账户或标的；不自动合并 SQLite 多快照。未命中以下唯一映射、目标现金账户不存在或不属于当前 owner、目标标的不存在或与动作不兼容、缺少对应新模型必填字段、权益日 override 或无价值 attestation 的旧行拒绝：

  | 旧 `module` | 旧 `action` | 新交易类型 | 额外转换约束 |
  | --- | --- | --- | --- |
  | `cash` | `deposit` / `withdraw` | `EXTERNAL_FUNDING` / `EXTERNAL_WITHDRAWAL` | 仅使用 `amount`。 |
  | `dividend`、`qqq` | `buy` / `sell` | `TRADE_BUY` / `TRADE_SELL` | 需要可唯一解析的标的、数量和价格。 |
  | `dividend`、`qqq` | `dividend` | `DIVIDEND` | 需要标的、税前 `amount` 和该 `sourceRow` 的明确 `entitlementDate`；旧数据没有税额时按零。 |
  | `dividend` | `interest` | `INTEREST` | 标的可空。 |
  | `put` | `buy` / `sell` / `expire` | `OPTION_OPEN` / `OPTION_CLOSE` / `OPTION_EXPIRE` | 需要完整期权主数据；`expire` 还必须有同 `sourceRow` 的 `expiryOutcome=WORTHLESS` 明示确认，到期归零按剩余数量。 |
  | `ic` | `futures_deposit` | `FUTURES_MARGIN` (`IN`) | 仅使用 `amount`，先建立同币种现金账户。 |
  | `ic` | `buy` / `sell` | `FUTURES_OPEN` / `FUTURES_CLOSE` | 需要可唯一解析的 IC/IM 合约、整手数量、点位、保证金和主数据乘数。 |
  | `ic` | `roll` | `FEE` | 已确认：旧行缺少可验证的两腿时不伪造换仓，只按 `fee` 追加手续费并在预览标记 `ROLL_FEE_ONLY`；零费用行 `SKIPPED`。 |

  对 `dividend`、`qqq`、`put` 模块的 `buy`/`sell` 行，旧 `price` 一律视为标的原币种十进制报价，精确乘以 `100` 写入 `unit_price_cent`（`USD/CNY 6.66 → 666`），与 `amountUnit` 完全独立；乘积不能精确为整数分即逐行拒绝。`amountUnit` 只决定旧 `amount` 的换算。

  `ic.margin`、`internal_in`、`internal_out`、`fee` 及未列出的历史动作没有无歧义的新账务语义，本切片 dry-run 必须拒绝，并给出行级 `IMPORT_ACTION_UNSUPPORTED`；不得根据金额符号或备注猜测方向。
- B-17：每个导入文件、对账证据、preview 和 job 必须属于当前 owner；服务端生成的上传凭据仅可写入该 owner 的隔离对象键。`POST /ledger/imports` 只接受 `direction=IMPORT` 的 file ID，手工对账附件只接受 `direction=RECONCILIATION_EVIDENCE` 的 file ID，二者不得互换或重用。每次 upload request 都创建新的 `import_export_file_id` 和独立物理对象，不按内容哈希复用，以保证每份文件从创建时起恰好保留 30 天。小程序只能通过 `wx.chooseMessageFile` 选择文件，并以 `wx.uploadFile` 执行服务端返回的 HTTPS 预签名 **POST**（`fileField` 与 `formData` 不可由客户端改写）；上传成功后必须以幂等 `POST /files/{importExportFileId}/scan-requests` 申请扫描，该申请只允许将本 owner 的 `UPLOAD_PENDING` 文件推进到隔离队列，不代表文件可信。带数据库租约的 worker 独占领取队列项。上传和证据对象必须启用服务端加密，使用 owner + file ID 作为加密上下文，并在 `import_export_file.encryption_key_version` 记录密钥版本；客户端无权选择加密算法或密钥。客户端声明的 MIME、大小和 SHA-256 不可信，worker 必须在读取对象后重新验证并扫描；扫描成功后复制到该文件专属的不可变证据对象键并删除隔离副本。导入原件与对账附件加密保留 30 天后删除，账务事实、审计和导入/对账摘要长期保留。任何所有权、方向、对象键、哈希、加密上下文、上传方法或表单字段不匹配均拒绝。
- B-18：体验版和正式版必须启用真实 `wx.login → code2Session → openid HMAC allowlist → opaque session` 链路；不得启用 local Mock、手填 code、HTTP、IP 地址、未登记 request/upload 域名或关闭 URL 校验。发布前必须通过隐私保护指引、个人信息处理与 30 天附件保留声明、主体/类目/备案、HTTPS 证书、数据源许可和真机回归门禁；任一缺失即阻断提审。

### 3.3 边界条件

- E-1：现金余额、保证金余额或持仓数量不足 → `422 INSUFFICIENT_BALANCE` 或 `422 INSUFFICIENT_POSITION`，不写任何部分分录。
- E-2：同日同标的多笔交易 → 以 ULID 和 `detail_no` 稳定排序，保证 FIFO 可重放。
- E-3：资金或交易金额恰为 `Long.MAX_VALUE`、金额乘数量/乘数溢出、期货点位计算产生非整数分、税额大于税前总额 → `400 MONEY_OVERFLOW`、`400 PRICE_PRECISION_INVALID` 或 `400 TAX_INVALID`。
- E-4：导入 dry-run 后文件被替换、checksum 不符、job 过期、已确认或状态非成功 → `409 IMPORT_PREVIEW_STALE`。
- E-5：同一账户/标的被并发写入 → 以事务内读取、乐观版本/唯一键和幂等记录保护；冲突返回 `409 VERSION_CONFLICT`，不进行静默重试。
- E-6：估值 `validUntil` 已过、仓位已为零或目标标的不存在 → 不把过期/不适用估值带入总览；返回明确状态。提交零估值按 P-1 拒绝。
- E-7：期货移仓平仓腿或开仓腿任一校验失败 → 整个 operation group 回滚。
- E-8：请求读取、确认或引用不属于当前 owner 的账户、交易、文件、preview 或 job → 返回 `404 RESOURCE_NOT_FOUND`，不得泄露其存在性。
- E-9：公司行为不是拆股、并股、送股，比例不是正整数比，不存在生效日未平批次，要求现金代替零股，或变换后数量超过 8 位小数 → `422 CORPORATE_ACTION_UNSUPPORTED`、`422 CORPORATE_ACTION_NO_OPEN_POSITION` 或 `400 CORPORATE_ACTION_RATIO_INVALID`。
- E-10：期货结算日不单调、重复结算、无未平合约、交割日后写入期货交易或到期日仍未平仓，期权到期日/数量不匹配、未提供 `WORTHLESS` 确认，或期权价值状态无法确认 → `409 FUTURES_SETTLEMENT_CONFLICT`、`422 FUTURES_MATURITY_ACTION_REQUIRED`、`400 OPTION_EXPIRY_OUTCOME_REQUIRED`、`422 OPTION_EXPIRY_DATE_OR_QUANTITY_INVALID` 或 `422 OPTION_EXPIRY_VALUE_UNCERTAIN`。
- E-11：历史补录/更正全量重放使任一中间日期余额、批次、保证金、结算基准或公司行为比例不成立 → `422 REPLAY_INVARIANT_VIOLATION`，不写任何新事实。
- E-12：超过服务端配置的 IP、openid HMAC 或会话限流阈值 → `429 RATE_LIMITED`；交易、上传凭据、导入确认和对账写入均不得由客户端自动重试。

### 3.4 降级策略

- D-1：Redis 会话不可用 → 继承 Phase 1，所有私有读写返回 `503 AUTH_SESSION_STORE_UNAVAILABLE`，绝不匿名降级。
- D-2：MySQL 不可用/事务失败 → 返回 `503 DEPENDENCY_UNAVAILABLE`，不把任何上传对象标为 `SCANNED`、`PREVIEWED` 或 `COMMITTED`，不确认导入、不创建部分事实。若对象复制已发生而随后数据库事务失败，worker 必须按 file ID 清理隔离/证据副本并由 orphan scavenger 兜底，不得留下可被其他请求引用的成功对象。
- D-3：MinIO 上传、对象校验或读取失败 → 导入 job 标为 `FAILED`，返回 `503 IMPORT_STORAGE_UNAVAILABLE`，不写账务事实。
- D-4：导入 worker 在 dry-run 失败 → `NEEDS_REVIEW` 并返回逐行错误；命令提交前的任何投影重放失败必须按 B-13 整体回滚，绝不进入本降级路径。只有已提交版本之后由独立“投影完整性校验/重建”任务发现读模型无法重建时，才标记投影降级并告警；账务事实不回滚、不自动修改，私有读取返回明确的 `PROJECTION_DEGRADED` 状态。

### 3.5 时序与状态机

- T-1：Market 标的先由独立 Market Data 命令创建/读取 → Ledger 锁定 `ledger_state`、校验账户并准备系统科目 → 生成交易模板 → 平衡/FIFO/余额校验 → 分配连续版本 → 写幂等记录、账务事实、审计、outbox → 投影 → 更新版本与幂等成功，全部在同一个 MySQL 本地事务。
- T-2：交易提交前任一步失败 → 回滚 T-1 中的全部数据库写入；不得创建 Redis 或半完成幂等状态。上传对象不属于 T-1，而属于已完成的文件上传状态机；T-1 不得把它推进为 `SCANNED`、`PREVIEWED` 或 `COMMITTED`，失败后的副本按 D-2 清理。
- T-3：导入状态只能为 `PENDING → RUNNING → SUCCEEDED|FAILED|NEEDS_REVIEW`；只有 `SUCCEEDED` 且 checksum 匹配可 `confirm`；确认后生成不可重复的账务事实。
- T-4：更正顺序必须为原交易 → 反向冲正 → 可选替代；不能直接替换或删除。`REVERSAL` 与已被直接冲正的目标不得重复冲正；替代交易作为正常业务事实可在后续继续进入同一 root 的更正链，并使用新的连续 `revision_no`。
- T-5：期货移仓中平仓和开仓必须共享 operation group，任一腿失败则整组不存在。
- T-6：导入对象的状态只能为 `UPLOAD_PENDING → QUARANTINED → SCANNED → PREVIEWED → COMMITTED → DELETED`，或失败终态；小程序上传成功后只可幂等申请 `UPLOAD_PENDING → QUARANTINED`，带租约 worker 独占执行后续二次验证；上传成功不等同于允许导入，只有 worker 的二次验证后才能进入 dry-run。
- T-7：对账写入先以 `reconciliationDate` 重放只读投影、计算现金/持仓差异、持久化对账事实与附件哈希；任何差异只产生 `NEEDS_REVIEW`，绝不回写账务事实。
- T-8：对象保留 worker 按每条 `import_export_file.created_at` 在创建满 30 天后删除其专属原件或对账附件；对仍指向隔离键的失败/中断扫描项，同时删除同一 owner/file/hash 的确定性证据键，避免复制后数据库更新前中断留下孤儿对象。仅保留不可逆 SHA-256、审计摘要和删除审计；删除失败告警并重试，不延长已签名上传 URL。

## 4. Code 映射

| HTTP | code | 触发规则 | 可重试 |
| --- | --- | --- | --- |
| 200/201/202 | `OK` | B-1..B-18、E-2、E-6、T-1..T-8 的成功路径 | 否 |
| 400 | `MONEY_FORMAT_INVALID` | P-1 | 否 |
| 422 | `CURRENCY_MISMATCH` | P-2 | 否 |
| 400 | `QUANTITY_INVALID` | P-3 | 否 |
| 400 | `TRANSACTION_FIELDS_INVALID` | P-4 | 否 |
| 409 | `INSTRUMENT_CONFLICT` | P-5 | 拉取既有标的后重试 |
| 400 | `REQUEST_VALIDATION_FAILED` | P-6 | 否 |
| 422 | `IMPORT_VALIDATION_FAILED` | P-7、D-4 的 dry-run 分支 | 修正文件后新建导入 |
| 422 | `IMPORT_ACTION_UNSUPPORTED` | B-16 | 删除或改造该旧行后新建导入 |
| 422 | `INSUFFICIENT_BALANCE` | E-1 | 资金补足后新建交易 |
| 422 | `INSUFFICIENT_POSITION` | B-8、B-9、E-1 | 修正数量后新建交易 |
| 400 | `MONEY_OVERFLOW` / `PRICE_PRECISION_INVALID` / `TAX_INVALID` | E-3 | 否 |
| 409 | `IMPORT_PREVIEW_STALE` | E-4、T-3 | 重新 dry-run |
| 409 | `VERSION_CONFLICT` | E-5 | 拉取后重新确认 |
| 404 | `RESOURCE_NOT_FOUND` | E-8 | 否 |
| 422 | `CROSS_CURRENCY_UNVALUED` | B-15 | 否，按币种查看 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | B-13 | 否 |
| 422/400 | `CORPORATE_ACTION_UNSUPPORTED` / `CORPORATE_ACTION_NO_OPEN_POSITION` / `CORPORATE_ACTION_RATIO_INVALID` | E-9 | 否 |
| 409/400/422 | `FUTURES_SETTLEMENT_CONFLICT` / `OPTION_EXPIRY_OUTCOME_REQUIRED` / `OPTION_EXPIRY_VALUE_UNCERTAIN` | E-10 | 修正后新建交易 |
| 422 | `REPLAY_INVARIANT_VIOLATION` | E-11 | 修正历史交易后新建命令 |
| 429 | `RATE_LIMITED` | E-12、B-18 | 是，按 `Retry-After` 由用户手动重试 |
| 503 | `AUTH_SESSION_STORE_UNAVAILABLE` | D-1 | 是 |
| 503 | `DEPENDENCY_UNAVAILABLE` | D-2 | 是 |
| 503 | `IMPORT_STORAGE_UNAVAILABLE` | D-3 | 是 |
| 503 | `PROJECTION_DEGRADED` | D-4 | 是，等待受控重建完成后刷新；不得读取部分或伪造快照。 |

## 5. 接口 I/O 示例

### 5.1 创建 CNY 现金账户

```http
POST /api/v1/ledger/accounts
Idempotency-Key: 01K8D3SF1YSMX0MNP1E5ZJQ2AB

{"displayName":"中信证券资金账户","currency":"CNY"}
```

```json
{"accountId":"01K8D3TQ2S3B9M1P0A7V6X4C2D","displayName":"中信证券资金账户","accountKind":"ASSET_CASH","currency":"CNY","version":"0"}
```

### 5.2 买入 ETF 并附带费用

```json
{"transactionType":"TRADE_BUY","occurredOn":"2026-07-26","cashAccountId":"01K8D3TQ2S3B9M1P0A7V6X4C2D","instrumentId":"01K8D44A1FJ7K3C8M5V0R2S6T9","quantity":"100.00000000","unit_price_cent":"400","fee_cent":"5","note":"长期配置"}
```

```json
{"transactionId":"01K8D43J4YFN7X9R2B6C8M0V3P","transactionType":"TRADE_BUY","occurredOn":"2026-07-26","currency":"CNY","postings":[{"postingId":"01K8D43J4Z2X8T1P6A9M0C5R7S","accountId":"01K8D44B9YQ8V2H6M1P0X3C5D7","postingSide":"DEBIT","amount_cent":"40005","currency":"CNY"},{"postingId":"01K8D43J52C9X3N5Z8P0Q1R4S6","accountId":"01K8D3TQ2S3B9M1P0A7V6X4C2D","postingSide":"CREDIT","amount_cent":"40005","currency":"CNY"}],"tradeDetails":[{"tradeDetailId":"01K8D43J53D0Y4P6A9Q1R2S5T7","instrumentId":"01K8D44A1FJ7K3C8M5V0R2S6T9","positionEffect":"OPEN","quantity":"100.00000000"}],"ledgerVersion":"1"}
```

### 5.3 分红及代扣税

```json
{"transactionType":"DIVIDEND","occurredOn":"2026-07-26","cashAccountId":"01K8D3TQ2S3B9M1P0A7V6X4C2D","instrumentId":"01K8D44A1FJ7K3C8M5V0R2S6T9","amount_cent":"1000","tax_withheld_cent":"100","note":"季度现金分红"}
```

```json
{"transactionId":"01K8D4AM7E4G2P9R5S1T6V8W0X","transactionType":"DIVIDEND","currency":"CNY","postings":[{"postingSide":"DEBIT","amount_cent":"900","currency":"CNY"},{"postingSide":"DEBIT","amount_cent":"100","currency":"CNY"},{"postingSide":"CREDIT","amount_cent":"1000","currency":"CNY"}],"ledgerVersion":"2"}
```

### 5.4 无效跨币种交易

```json
{"code":"CURRENCY_MISMATCH","message":"现金账户、标的和金额币种必须一致，且不支持隐式换汇","traceId":"01K8D4CB4J8P2S6V0X3Y5Z7A9B","details":["cashAccountId","instrumentId"]}
```

### 5.5 JSON 导入 dry-run 失败

```json
{"jobId":"01K8D4J0K3M6P9R2T5V8X1Z4B7","status":"NEEDS_REVIEW","preview":{"acceptedRows":1,"rejectedRows":1,"checksum":"a3d4e6f79b0c2d5e8f1a4b7c9d0e2f5a6b8c1d3e4f7a9b0c2d5e8f1a4b7c9d0","errors":[{"row":2,"code":"IMPORT_VALIDATION_FAILED","field":"currency","message":"旧流水未提供币种映射"}]},"error":null}
```

## 6. 外部依赖行为约定

### 6.1 MySQL 8.4（JDBC/MyBatis/Flyway）

- 用途：账务事实、投影、幂等、审计、导入 job 元数据和 schema 迁移。
- 一致性：B-1 至 B-15 的同步写入使用一个 Spring 本地事务；数据库错误遵循 D-2。
- 重试：客户端可用新的 Idempotency-Key 重试失败请求；服务端不得在未知提交结果时生成重复分录。
- 验证：单元测试覆盖领域不变量；可用 Docker 的环境用 Testcontainers 验证真实事务、索引和 Flyway。

### 6.2 Redis（Spring Data Redis）

- 用途：仅身份会话与短期读缓存；不保存账本真相、导入唯一状态或原件。
- 失败：遵循 D-1；账本接口不提供匿名读取降级。

### 6.3 MinIO（S3 兼容本地对象存储）

- 用途：隔离上传的 JSON/SQLite 原件与手工对账证据；`platform_db.import_export_file` 只保存 object key、哈希、媒体类型、字节数、方向、状态与服务端加密密钥版本。
- 对象键：隔离键固定为 `quarantine/{owner_user_id}/{import_export_file_id}`；扫描成功后复制为 `evidence/{owner_user_id}/{import_export_file_id}/{content_sha256}`。每个 file ID 的物理证据对象独立，客户端永远不能指定 key，也不得以内容哈希复用其他文件的对象。
- 接口：只签发适配 `wx.uploadFile` 的单对象预签名 **POST**；响应必须提供 `method=POST`、固定 `fileField` 和完整 `formData`，一次上传仅绑定 owner、隔离 key、SHA-256、媒体类型、服务端加密算法/上下文和 `<IMPORT_MAX_BYTES>`。上传成功后，小程序以幂等扫描申请把该 owner/file ID 排入 worker；worker 以 `scan_lease_until` 独占领取，避免多实例重复扫描。本地 MinIO 使用受控 SSE 配置，生产对象存储使用 `<KMS_KEY_ID>`；应用仅记录密钥版本且绝不记录密钥材料。默认本地上限为 10 MiB，导入允许 `application/json`、`application/x-sqlite3`，对账证据允许 `application/pdf`、`image/jpeg`、`image/png`，默认 TTL 为 15 分钟。
- 小程序发布契约：上传 URL 必须是已配置 HTTPS 合法上传域名的 `https://upload.<APP_PUBLIC_DOMAIN>`，由反向代理转发到对象存储；不得向体验版/正式版返回局域网 IP、裸 MinIO 端口或未登记域名。开发者工具本地调试可使用 local profile 的受控直连配置。
- 失败：对象不存在、哈希不符、媒体类型不符、POST 表单不符、扫描失败或读取失败均遵循 P-7/D-3；不创建账务事实。
- 真实链路待验证：生产对象存储 IAM、恶意文件扫描、30 天加密保留/删除审计与上传域名在体验版前完成；这些项目沿用用户已授权的部署 TBD，但不阻断 local 编码。

### 6.4 SQLite 导入驱动

- 用途：只读解析旧 `snapshots(id,created_at,payload_json)`；不执行任何来自上传 SQLite 的 SQL，不写回上传文件。
- 约束：只接受白名单表名和单个 `snapshotId`；payload 必须是 JSON 对象且根 `entries` 为数组。任一结构异常遵循 P-7。
- 真实链路待验证：不同 SQLite 版本、恶意文件扫描和大文件压缩攻击防护在发布前联调；本地测试使用受控 fixture。

## 7. 动态内容生成规则

- 小程序账本页展示服务端返回的单币种金额字符串、账务状态、冲正链、公司行为/逐日结算状态和数据截止时间；不得生成投资建议、虚构行情、混币总资产或模拟收益。
- 交易预览由服务端根据模板返回完整复式分录影响；小程序只展示、二次确认和提交，不能在客户端自行计算会计结果。
- 导入页显示 dry-run 的接受/拒绝条数、逐行错误、目标现金账户/标的/币种映射、金额单位转换与 checksum；文件仅经 `wx.chooseMessageFile` 选择并使用服务端 POST 表单上传，必须二次确认才允许提交。

### 7.1 小程序页面与状态

| 页面 | 主流程 | 必须展示/阻止的状态 |
| --- | --- | --- |
| 账本总览 | 首次无账户 → 创建 CNY/USD 现金账户 → 外部入金 → 查看按币种分组的现金、持仓与未估值状态 | 无账户时只展示开户引导；不得显示零值伪资产或混币总资产。收到 `PROJECTION_DEGRADED` 时隐藏可能部分重建的数值并提示受控重建中。 |
| 新增交易 | 选择动作与现金账户 → 如无标的，在同页弹层调用 Market 标的创建接口 → 填写动作矩阵字段 → 请求服务端预览 → 二次确认提交 | 期货结算必须明确标为“手工”；期权到期必须二次确认“无价值”。预览与提交期间禁用重复点击；预览版本变化、余额不足、会话失效、投影降级或网络失败必须清晰提示并允许重新预览。 |
| 流水详情 | 查看分录、FIFO 批次影响、冲正链、公司行为与账本版本 → 选择“冲正”或“冲正并替代” | 不提供编辑/删除按钮；历史冲正必须提示“将重述后续历史报表”；若目标属于期货移仓，必须说明将原子冲正整组两腿；冲正目标已冲正时禁用并显示原因。 |
| 持仓与估值 | 按币种、现金账户和标的查看持仓；录入手工单价或用户级总市值与有效期 | `MANUAL`、`UNVALUED`、`EXPIRED`、`PRECISION_UNAVAILABLE`、`MANUAL_TOTAL_UNALLOCATED`、`FUTURES_SETTLEMENT_ONLY`、`CROSS_CURRENCY_UNVALUED` 必须显式显示；总市值绝不分摊到账户。 |
| 导入 | 通过 `wx.chooseMessageFile` 选择 JSON/SQLite → POST 上传 → 选择单一快照、目标现金账户、标的、货币/单位映射；对缺失权益日的分红填写逐行权益日、对期权到期行逐行确认“无价值” → 轮询 dry-run → 阅读错误/差异 → 输入二次确认 → 提交 | 预览过期、哈希改变、任一拒绝行、映射/权益日/无价值确认缺失、上传域名或 POST 表单错误时，不出现提交按钮。 |
| 手工对账 | 选择现金账户和对账日 → 手工输入券商现金、持仓数量并可附证据 → 提交差异检查 | 只显示 `MATCHED` 或 `NEEDS_REVIEW`；不显示“自动修复”，不能从对账页生成调账交易。 |

### 7.2 前端金额与请求规则

- 表单金额以字符串收集，使用严格正则和后端预览验证；禁止 `Number()`、`parseFloat()` 或浮点四则运算。
- 展示层将 `*_cent` 字符串格式化为对应币种两位小数；数量、期货点位可使用独立十进制字符串格式化器，但不得被当作货币。
- 每次可重试写请求创建新的 `Idempotency-Key`；网络超时后的同一提交必须复用原 key 读取首次响应，不能生成第二笔交易。收到 `429 RATE_LIMITED` 时展示 `Retry-After`，由用户主动再次提交，禁止自动重试交易、导入确认、对账和上传凭据请求。
- 小程序只缓存会话 token、最近的只读 ViewModel 和表单草稿；不缓存可提交的账务事实、不离线写账。体验版/正式版必须使用独立构建配置：HTTPS API/上传域名、`urlCheck=true`、无 local 登录入口、无 IP/HTTP 地址、无测试密钥或 `node_modules` 源目录；导入、图表和策略详情使用分包。

## 8. 性能与安全约束

### 性能

- 单用户本地目标：非历史账本写入和读模型查询 P95 小于 500 ms；历史补录/冲正的全量重放及导入确认以最多 1000 条流水为硬上限、目标 P95 小于 5 秒。超出上限拒绝并要求拆分导入，不允许静默异步化后失去单事务原子性；单页最多 100 条。
- 所有列表走 owner + 时间/账户/标的索引；投影在同一事务同步完成，导入的重放由 job 执行。

### 安全

- 所有私有接口必须经过 Phase 1 会话过滤器；对象上传 URL 不可列目录、不可覆盖其他 owner 对象、不可长期使用。
- 日志只能记录 ULID、哈希、动作、金额字段名和错误码；不得记录 token、原件内容、完整备注、上传 URL 或密钥。
- 上传内容按大小、哈希、MIME、SQLite 表白名单和 JSON 结构验证；生产环境的恶意文件扫描为发布前必须完成项。
- 交易事实不可删除；账户停用只阻止后续使用，不删除历史。
- 运行时数据库权限必须禁止应用账户对 `ledger_transaction`、`ledger_posting`、`ledger_trade_detail` 的 `UPDATE`、`DELETE`；仅授予 `SELECT`、`INSERT`。Flyway 专用迁移账户与业务应用账户隔离，投影与账户表按最小权限单独授权。

### 测试边界声明

- **Unit：** 金额解析、币种、分录平衡、交易模板、FIFO、期货多头、期权到期、冲正链、导入 JSON 结构、领域依赖方向。
- **Integration：** MySQL 事务、幂等并发、Flyway V3+、Redis 拒绝、MinIO 签名/哈希、SQLite fixture dry-run、REST 安全。
- **E2E/Manual：** 微信开发者工具上传、真机网络、实际对象存储权限、生产恶意文件扫描、真实微信和云部署。

## 9. 测试矩阵

| # | 测试场景 | 规则 | 类型 | 本地可跑 | 验证层级 | 验收证据 | 预期 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 金额字符串、long 边界、税额边界 | P-1、E-3 | Unit | 是 | Unit | JUnit | 非法值拒绝，无浮点。 |
| 2 | 币种隔离和禁止 FX | P-2、B-15 | Unit | 是 | Unit | JUnit | 不生成混币总额。 |
| 3 | 数量精度与期货整手 | P-3 | Unit | 是 | Unit | JUnit | 精确到 8 位/期货整数。 |
| 4 | 每种交易的字段矩阵 | P-4 | Unit | 是 | Unit | JUnit 参数化测试 | 缺字段拒绝。 |
| 5 | 表单先创建标的、Ledger 仅接收 `instrumentId` | P-5、T-1 | Integration/Architecture | 是 | Integration | API/架构测试 | 不产生 Ledger→Market 写依赖。 |
| 6 | 账户、系统科目、保证金双账户创建与 If-Match 停用 | P-6、B-1、B-2、E-5 | Integration | 是 | Integration | MySQL 查询 | 仅现金账户由用户创建；有余额、未平仓、锁定保证金或待办任务时不得停用。 |
| 7 | 所有交易模板分录平衡 | B-3..B-11 | Unit | 是 | Unit | JUnit | 每币种借贷相等。 |
| 8 | 买卖 FIFO、部分卖出、超卖与买入费资本化 | B-5、B-8、E-1、E-2 | Unit | 是 | Unit | JUnit | 成本、买入费和卖出净损益可精确重放。 |
| 9 | 分红权益日、代扣税与独立税费科目 | B-6、B-7 | Unit | 是 | Unit | JUnit | 税前收入、税后现金、税费分类和无法核验导入均正确。 |
| 10 | 期货合约主数据、手工逐日结算、仅多头开平、保证金按批次分配、点位精度、到期边界与移仓原子性 | B-9、B-10、E-3、E-7、E-10 | Unit/Integration | 是 | Integration | 事务断言 | 乘数不可由交易覆盖；每日盈亏仅结算一次；交割日后不能开平/移仓且未平仓明确阻断；无舍入、无空头、无半组写入。 |
| 11 | 期权精确权利金、开平与无价值到期核销 | B-11、E-10 | Unit/Integration | 是 | Integration | JUnit/MySQL | 非货币乘数参与精确计算且写入账本快照；后续主数据查询变化不得改变历史重放；到期日必须整仓核销，缺失无价值确认或价值不确定时拒绝。 |
| 12 | 冲正/替代不可变链、连续版本号、期货移仓整组更正与历史重放失败回滚 | B-12、T-4、E-11 | Integration | 是 | Integration | MySQL 查询/重放快照 | 无 UPDATE/DELETE；冲正与替代使用同一 root 的连续不重复版本号；普通交易只产生一条冲正，移仓两腿必须同时冲正/替代；任何中间日不变量失败时无新增事实。 |
| 13 | 幂等、连续账本版本、历史补录与并发 | B-13、B-14、E-5、T-1/T-2 | Integration | 是 | Integration | 并发测试 | 一次事实或 409；历史报表按最新版本重述。 |
| 14 | 投影重放、Ledger 单向依赖与降级边界 | B-14、D-4 | Unit/Integration | 是 | Integration | 重放快照/故障注入 | 命令内重放失败整体回滚；独立校验重建失败时只暴露 `PROJECTION_DEGRADED`，不返回部分读模型。 |
| 15 | 估值优先级、过期、跨币种与手工对账 | B-15、B-15a、E-6、T-7 | Unit/Integration | 是 | Integration | JUnit/MySQL | 不污染成本；对账只标记差异，不自动调账。 |
| 16 | JSON/SQLite 单快照、现金账户/标的/币种/单位映射、权益日/无价值确认与不支持动作 | P-7、B-16、T-3 | Integration | 是 | Integration | 受控 fixture | dry-run 不写账本；未命中映射、跨 owner 目标、缺失逐行权益日/无价值确认或含歧义旧动作拒绝。 |
| 17 | 导入 checksum、所有权、隔离对象、服务端加密、独立生命周期、重复确认和部分失败 | B-17、E-4、E-8、D-3、D-4、T-6、T-8 | Integration | 是 | Integration | MySQL/MinIO 测试 | 无越权、无重复或半入账；缺失/篡改加密上下文拒绝；相同哈希的两次上传仍各有独立对象和独立 30 天删除时间。 |
| 18 | Redis/MySQL 故障拒绝 | D-1、D-2 | Integration | 是 | Integration | 故障注入 | 503，无匿名读写。 |
| 19 | Flyway V3、无外键、金额列、语义 ID、公司行为、税费科目与账务表权限 | B-3、B-6、B-8、B-14 | Integration | 是（需 Docker） | Integration | Testcontainers/权限查询 | Schema gate、约束名校验与最小权限通过。 |
| 20 | 交易预览不落库 | §2.3、§7 | Integration | 是 | Integration | MySQL 计数断言 | 返回模板但不写业务事实、幂等或版本。 |
| 21 | 小程序录入、预览、确认、冲正、导入、对账与上传域名 | §7、§8 | E2E | 否 | E2E/Manual | 开发者工具截图/API 日志 | UI 不伪造金额或状态，体验版使用 HTTPS 合法 request/upload 域名。 |
| 22 | 拆股、并股、送股与不支持公司行为 | B-3、B-8、E-9 | Unit/Integration | 是 | Integration | 重放快照 | 总成本不变，数量精确变化；现金代替零股、配股、分拆等被拒绝。 |
| 23 | 真实微信登录与会话失效 | B-18、E-12 | E2E/Manual | 否 | E2E/Manual | 真机脱敏日志 | `wx.login`、allowlist、30 分钟闲置/8 小时绝对过期与即时失效有效。 |
| 24 | `chooseMessageFile`、预签名 POST、30 天删除 | B-17、T-8 | Integration/E2E | 部分 | Integration/E2E | MinIO/真机证据 | 表单篡改拒绝；原件/附件过期删除且摘要保留。 |
| 25 | 发布构建、隐私门禁、限流 | B-18、E-12 | E2E/Manual | 否 | E2E/Manual | 提审配置/真机/压测证据 | 无 HTTP/IP/local Mock；隐私、域名、证书、限流和分包门禁通过。 |

## 10. 数据库增量与代码边界

V1/V2 为已执行迁移，**不得修改**。V3 的前置断言是 Phase 1 的 Ledger 表及 `platform_db.import_export_file` 没有业务事实；若检测到已有事实，迁移必须停止并要求专门的数据迁移方案。V3 至少包含以下结构；所有关系都是语义业务 ID，**不建立外键**。

```sql
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
  CHECK (strike_price_cent > 0), CHECK (contract_multiplier > 0)
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
  CHECK (open_quantity >= 0), CHECK (open_quantity = FLOOR(open_quantity)),
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
```

- 所有 `*_cent` 均为 `BIGINT`，所有新增实体均有自增 `id` 与同名语义 ULID；`DECIMAL` 仅用于非货币数量、点位和比例。期权 `contract_multiplier` 与其账本快照 `option_contract_multiplier`、公司行为比例和对账数量不是货币，禁止以 `_cent` 命名。`ledger_corporate_action`、`ledger_income_detail` 与 `portfolio_reconciliation_position` 是父交易/对账事实的一对一或一对多明细，归属由语义化 `transaction_id` 或 `reconciliation_id` 唯一确定；不得重复存储可推导的 `owner_user_id`，权限校验须经父实体完成。
- V3 实施前必须用 `SHOW CREATE TABLE ledger_db.ledger_account` 校验 V1 生成的 CHECK 约束名；若目标 MySQL 生成名不是示例中的 `ledger_account_chk_1`，迁移脚本必须使用实际名称替换，禁止跳过将 `EXPENSE_WITHHOLDING_TAX` 纳入约束的迁移。
- `uk_import_export_content` 是 V1 的内容去重索引；V3 必须按上述名称删除它并改为普通检索索引，避免不同上传请求共享一个 30 天到期对象。若 V1 实例索引名被人为改动，迁移必须停止并先完成同名预检，不得跳过删除。
- `portfolio_position_lot.opened_cost_cent`/`remaining_cost_cent` 是 FIFO 分摊唯一真相；`unit_cost_cent` 是向下兼容的展示字段。若总成本除以数量无法精确为分，API 只展示非权威的格式化平均成本并同时返回总成本与数量，禁止以展示均价参与分录或下次 FIFO 计算。V3 前置断言“无业务事实”确保新增非空成本列不需要猜测历史值。
- 应用数据库授权脚本在 Phase 2 前必须由 schema 级广泛 DML 改为表级最小权限：账务事实表只读追加，投影表允许受控重建，Flyway 使用单独的迁移凭据。
- Ledger、Portfolio、Market Instrument、Platform Import 的 domain/application/infrastructure/interfaces 分层与 Phase 1 Identity 同样隔离。Ledger Domain 不得依赖 Spring、MyBatis、HTTP、Redis、MinIO 或小程序 DTO。
- 账户/标的/交易写入和投影要有架构测试；导入、对象存储和 SQLite 均在 infrastructure adapter 后，应用层只依赖 port。

## 11. 风险与部署占位符

| 风险 | 本地实现决策 | 发布前门禁 |
| --- | --- | --- |
| 用户上传文件 | 10 MiB、`wx.chooseMessageFile`、预签名 POST、hash/结构验证 | 接入恶意文件扫描、对象存储 IAM、30 天加密保留与删除审计。 |
| MinIO 本地运行 | Compose 增加 MinIO、bucket 初始化、健康检查和 `.env` 的 endpoint/凭据变量 | 体验版使用 HTTPS 反向代理、已登记 upload 域名和真机 `wx.uploadFile` 回归。 |
| 账务事实防篡改 | 应用层无 UPDATE/DELETE + 数据库表级仅 SELECT/INSERT | 在 CI 验证 `SHOW GRANTS`，迁移账户与应用账户隔离。 |
| MySQL 8.4/Flyway | 继续使用已验证的 V1/V2 本地组合 | 在 CI/Docker 环境执行 Testcontainers；解决 Flyway 8.4 警告。 |
| 云资源与域名 | 本地 Compose + 环境变量 | 用 `<CLOUD_*>`、AppID、合法域名、密钥和备份策略替换。 |
| 微信发布 | local profile 允许 Mock/HTTP；发布构建禁止 | 真实 `wx.login`、code2Session、隐私指引、主体/类目/备案、HTTPS、`urlCheck=true`、request/upload 域名与真机验收全部通过。 |
| 券商数据 | 不接券商 API；手工录入、导入和对账 | 持续保持无券商 API 边界；差异只产生 `NEEDS_REVIEW`，不得自动调账。 |
| 外部行情 | 不接入，不伪造估值 | Phase 3 确认数据源许可、时效和 XXL-JOB。 |

## Spec 自检

| # | 检查项 | 状态 |
| --- | --- | --- |
| 1 | Part 3 每条规则均有 Code 映射 | ✅ |
| 2 | Part 3 规则均有 I/O 示例或测试矩阵覆盖 | ✅ |
| 3 | MySQL、Redis、MinIO、SQLite 均有失败策略 | ✅ |
| 4 | 测试矩阵覆盖参数、并发、导入、状态机和降级 | ✅ |
| 5 | 覆盖空值、溢出、币种、超卖、并发和半完成 | ✅ |
| 6 | 风险均已给出本地决策及发布门禁 | ✅ |
| 7 | 部署 TBD 已获用户授权使用占位符，且不阻断本地代码 | ✅ |
| 8 | 区分 Unit、Integration 与 E2E/Manual | ✅ |
| 9 | 写入、导入、冲正、历史重放、公司行为、结算与移仓状态时序已定义 | ✅ |
| 10 | 真实微信/对象存储下游均有体验版前发布门禁；未确认配置不得进入发布构建 | ✅ |
| 11 | 期货逐日结算、期权精确计价/无价值核销、手续费、税费、分红权益日与对账规则已定义 | ✅ |
| 12 | 无券商 API、无自动调账及不支持公司行为的边界已显式定义 | ✅ |
| 13 | 内部 `REVERSAL`、移仓两腿原子更正与唯一版本链已与 DDL 对齐 | ✅ |
| 14 | 历史导入的权益日/无价值确认、文件方向、独立 30 天生命周期与服务端加密已定义 | ✅ |

**批准条件：** 用户确认此规格后，按测试矩阵执行 TDD：先编写并运行失败测试，再实现 Java 后端、Flyway 增量、MinIO/SQLite adapter、OpenAPI 与小程序页面，最后执行集成和手工验收。
