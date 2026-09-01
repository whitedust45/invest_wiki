# Phase 2 下一阶段实施计划：可审计交易到微信小程序闭环

**状态：** 已进入实施与验收；依据已获批准的 [Phase 2 可执行规格](phase2-ledger-spec.md)，不新增业务范围或待确认决策。
**制定日期：** 2026-07-26  
**适用代码：** `new_project/`  
**硬约束：** `AGENTS.md` 的金额、业务 ID、无外键和本地提交规则优先。

## 1. 当前事实与目标

已完成的可验证基础：现金账户与一部分系统科目、原币种整数金额值对象、双重分录模板、按 owner 的连续 `ledger_version`、外部入/出金、内部转账、分红/利息/费用、账户 API、小程序现金账户页、V3 schema 与最小数据库授权。

初始缺口（制定计划时）：尚无 Market/Portfolio 上下文；没有通用交易 REST、交易预览、交易明细、FIFO 投影、历史重放、期货/期权/公司行为、估值/对账、文件上传/导入 worker、完整小程序写入链路或真实微信/部署验收。现有账户初始化、现金余额校验和幂等边界也尚未完全满足批准规格，必须先在 S0 收敛，不能作为后续交易的隐含前提。

本阶段目标是将已批准的 Phase 2 按依赖关系完成为可本地运行、可重放、可审计的小程序后端，而不是以页面占位或静态结构替代业务实现。

### 1.1 实施进度更新（2026-07-28）

| 切片 | 当前状态 | 已验证证据 | 剩余条件 |
| --- | --- | --- | --- |
| S0--S3 | 已实现 | 全量 Java Unit 通过；现金/现货 FIFO、冲正与公司行为、期货重放、期权开平仓/无价值到期均有覆盖测试。 | MySQL/Redis 真实事务与 Flyway 门禁待 Docker/Testcontainers 环境补跑。 |
| S4 | 部分完成 | 手工估值、按账户手工对账、`NEEDS_REVIEW`、按币种组合总览/持仓查询和小程序展示均已实现并通过 Unit/类型检查。用户级总市值只在总览计一次；期权单价按合约乘数计算；期货只纳入现金、保证金和已确认逐日结算。 | MySQL 真实查询、历史 `asOf` 重放和小程序真机验收待 Docker/真机环境。 |
| S5 | 已完成（本地实现） | MinIO 预签名 POST、owner 隔离、扫描队列租约、加密证据复制、30 天清理、JSON/SQLite 结构白名单、文件状态恢复和小程序上传已实现。dry-run 逐行映射、证据/映射上下文 checksum、rollback-only 账本演练、确认入账与 `IMPORT + import_export_file_id` 审计链路已实现；旧 Dashboard `price` 精确乘 100，`ic/roll` 仅导入费用并标记 `ROLL_FEE_ONLY`。 | MinIO/MySQL 真实集成待 Docker。 |
| S6 | 部分完成 | local 显式 profile、非 local 失败关闭配置守卫、扫描/保留定时任务、非 root 容器镜像和云 Compose 占位模板已具备。 | 真实微信、HTTPS/备案域名、生产恶意文件扫描、云资源、XXL-JOB 版本/许可/镜像/持久化和凌晨 Cron 均为发布门禁 `TBD`。 |

本机已执行 `mvn test`、`npm run typecheck`、OpenAPI YAML 解析和 `git diff --check`。`mvn test -Pintegration` 因当前机器不存在 Docker socket 而无法启动 Testcontainers；这不是集成测试通过的证据。

## 2. 实施顺序与交付物

| 顺序 | 垂直切片 | 后端交付物 | 小程序交付物 | 完成证据 |
| --- | --- | --- | --- | --- |
| S0 | 账本地基收敛 | 现金账户唯一性/停用门禁、规范系统科目、余额重放校验、单事务命令边界 | 账户错误与版本冲突状态 | Unit + MySQL Integration：重复账户、余额不足、幂等/并发/回滚 |
| S1 | 标的与现货 FIFO | `Market` 聚合、标的/合约主数据、现货买卖交易明细、精确总成本 FIFO、交易预览/提交/查询 | 标的创建、买卖表单、复式分录预览、流水列表 | Unit + MySQL 集成：买入、部分卖出、超卖、跨币种、历史补录 |
| S2 | 更正与公司行为 | 冲正/替代链、owner 锁内历史重放、拆股/并股/送股 | 更正入口、重放失败提示、公司行为录入 | Unit + Integration：不可变链、重放回滚、成本守恒 |
| S3 | 期货与期权 | CFFEX IC/IM 多头、保证金、手工结算、移仓原子组；买入期权、平仓、无价值核销 | 期货/期权专用表单、到期确认、移仓更正告警 | Unit + Integration：交割日期、整手、乘数快照、不可卖空/不可自动结算 |
| S4 | 组合、估值与对账 | 按币种投影、手工估值优先级/过期、全量手工对账与 `NEEDS_REVIEW` | 总览、持仓、估值和对账页 | Integration：投影版本、禁止混币、无自动调账 |
| S5 | 上传与导入 | 加密预签名 POST、隔离扫描、JSON/SQLite 单快照 dry-run、确认入账、30 天清理 worker | 文件选择、上传、映射、权益日/无价值确认、预览/确认 | MinIO + SQLite Integration：方向/归属/哈希/生命周期/重复确认 |
| S6 | 发布与运行质量 | 限流、指标、降级、XXL-JOB 仅用于后续行情/报表、部署 manifest、真实微信开关 | 离线只读、错误/降级状态、体验版/正式版构建门禁 | Docker/Testcontainers、真机、HTTPS 域名、隐私与发布清单 |

## 3. S0--S1 详细执行计划（本轮直接实现范围）

### 3.1 S0：已实现地基与批准规格对齐

1. 新增仅向前的 V4 Flyway 迁移。使用 `cash_display_name` 生成列（仅当 `account_kind='ASSET_CASH'` 时取显示名）和 `(owner_user_id, currency, cash_display_name)` 唯一索引，数据库层保证同一用户、同显示名、同币种的现金账户不可并发重复创建；不使用外键，不改写 V1--V3。
2. 删除当前“每币种预创建全局投资成本科目”的实现。`INV:{cash_account_id}:{instrument_id}` 只在首次成功现货/期权开仓命令的同一事务内创建；不得产生 `ASSET_CLEARING`。保留且只允许由服务端解析的 `SYS:{kind}:{currency}`、`MRGAV:{cash_account_id}`、`MRGLK:{cash_account_id}` 命名规则。
3. 为所有会减少现金或可用保证金的命令，在 owner 锁内从不可变分录重放得到按账户、币种分组的余额后验证；余额不足以 `422 INSUFFICIENT_BALANCE` 回滚，不得仅依赖易过期读模型。账户停用同样在锁内检查零余额、零未平仓/保证金及无 `PENDING`/`RUNNING` 导入，再以 `If-Match` 更新版本。
4. 统一写入命令边界为一个 Spring 本地事务：原子创建并 `SELECT ... FOR UPDATE` 锁定 `ledger_state`，验证/预演本命令与历史重放不变量，分配连续版本，写入幂等记录、不可变事实/明细、审计、outbox 与投影，更新状态版本，最后标记幂等成功后提交。任何失败均回滚；相同键同规范请求重放首次状态码和响应，相同键不同规范请求返回 `409 IDEMPOTENCY_KEY_REUSED`。
5. 幂等请求摘要必须包含认证 owner、HTTP 方法、路由、内容类型和键排序后的 JSON 字符串字段；禁止把货币字符串转换为浮点或依赖原始 JSON 键顺序。幂等记录保存实际响应状态和版本化响应体，不能把所有成功命令硬编码为 `201`。
6. 先补 S0 失败测试：重复现金账户的并发竞争、停用前置条件、取款/转出余额不足、同键并发、事实/审计/outbox/投影任一失败的全回滚；这些测试必须在现有实现上先失败。

### 3.2 Market 上下文

1. 创建 `market/{domain,application,infrastructure,interfaces}`，保持 Ledger Domain 不反向依赖 Market Infrastructure。
2. 以 `(market, exchange, symbol)` 为幂等自然键创建 `EQUITY`、`ETF`、`FUTURE`、`OPTION`；字段完全相同则返回既有实体，任何不同字段返回 `INSTRUMENT_CONFLICT`。
3. FUTURE 仅允许完整 IC/IM 元数据；OPTION 仅允许同币种 `EQUITY`/`ETF` 标的、PUT/CALL、行权价、到期日和非货币正整数乘数。所有货币乘数/行权价用 `*_cent BIGINT` / `long` / API 字符串。
4. 标的创建写入主表与对应 `future_contract` 或 `option_contract`，不建外键；所有读写只用 `instrument_id`、`future_contract_id`、`option_contract_id`。

### 3.3 现货交易与 FIFO

1. 建立通用 `TransactionCreate` 命令的严格白名单校验；未实现的命令类型不得静默接受或伪造成功。
2. `TRADE_BUY`：计算 `quantity × unit_price_cent` 时必须精确到整数分；买入手续费资本化到 `opened_cost_cent`/`remaining_cost_cent`。
3. `TRADE_SELL`：严格按 `(occurred_on, transaction_id, detail_no)` FIFO 消费批次；卖出数量不得超过剩余数量；卖出手续费单独记 `EXPENSE_FEE`，报告净已实现损益另行扣除。
4. 持仓批次以总成本和数量为唯一真相；展示均价不得参与下一次 FIFO 分摊。分数数量导致不可整分时，采用确定性、守恒且“全量卖出必清零”的余数归属算法，并以相同排序键在重放时重新计算。
5. 交易写入复用 S0 的单事务命令边界；历史补录先锁定 owner，再按 `(occurred_on, transaction_id, detail_no)` 从最早影响日期全量重放，任何中间日期余额或持仓不变量失败即 `422 REPLAY_INVARIANT_VIOLATION`，不得保留部分事实。

### 3.4 API、小程序与契约

1. 扩展 OpenAPI，所有金额字段均为 `type: string` 的十进制分，数量也是字符串；不允许 JSON number。服务端以严格 JSON token 反序列化器只接收字符串 token，避免 Jackson 把 number 静默强转为 `String`。
2. `POST /market/instruments`、`POST /ledger/transactions/preview`、`POST /ledger/transactions`、`GET /ledger/transactions` 均按规格增加认证、owner 隔离、幂等或预览无写入语义。流水查询使用已批准的稳定游标/账本版本语义和服务端上限，不允许客户端通过偏移量造成同页重复、遗漏或无限拉取。
3. 小程序以纯字符串格式化金额/数量；货币字段不得使用 `Number`、`parseFloat` 或算术隐式转换，枚举选择器索引除外。买卖提交采用“录入 → 预览 → 显式确认”三步，网络失败不自动重放写入。
4. 写入页实现 loading、empty、read-only-offline、submitting、succeeded、retryable-error、version-conflict、data-stale 八态；离线不保存待同步账务队列。
5. 对“提交结果未知”的 `retryable-error`，只提供用户主动触发的“按原确认内容重试”；它复用该确认动作已生成的 `Idempotency-Key`，表单内容改变或重新预览后才生成新键。禁止定时、页面重入或网络恢复时自动写入。

## 4. 质量门禁与 TDD 顺序

1. 每个领域规则先写 Unit 测试并运行红灯，再写最小实现转绿；不得先写 Happy Path 再补异常。
2. 每一切片完成后运行 `mvn test`、`npm run typecheck`、`git diff --check`。
3. MySQL/Redis/MinIO/SQLite 真实语义进入 Testcontainers Integration；Docker 不可用时保持测试代码与标记，但不得把 Unit 绿灯宣称为集成验证通过。
4. 每次 API 扩展同时更新 OpenAPI；每次 schema 变更使用新的 Flyway 迁移，绝不改写 V1/V2/V3。
5. 完成 S1 前不进入 S2；S2--S6 可在其依赖满足后并行拆分测试，但所有账务写入仍串行受 owner 锁约束。
6. API 契约测试必须覆盖“金额/数量 JSON number 被拒绝、字符串边界、错误码、响应状态、幂等响应重放、未知提交后的手动重试”；小程序测试覆盖同一确认动作仅保留一个幂等键且不发生后台重试。
7. 日志、审计和指标只记录 trace ID、业务 ID、枚举状态、耗时和脱敏错误码；不得记录 access token、openid、会话、上传表单、对象密钥、原始文件或完整账务备注。

## 5. 失败策略与发布边界

| 场景 | 本地实现策略 | 发布前验证 |
| --- | --- | --- |
| MySQL / Redis 不可用 | 写入和私有读拒绝，不降级为内存账本 | 容器故障注入，返回无敏感细节的 503 |
| 投影/历史重放失败 | 当前命令事务回滚；独立校验失败时读端 `PROJECTION_DEGRADED` | 版本对账与重建演练 |
| MinIO/扫描失败 | 文件保留隔离态，禁止 preview/confirm | 加密上下文、对象路径和删除审计 |
| 微信/云配置未知 | 只允许 local profile Mock；体验版/正式版 fail closed | 真机 `wx.login`、HTTPS、合法域名、隐私与备案材料 |
| Docker 当前不可用 | 不修改业务语义；保留并执行 Integration 测试入口 | Docker daemon 可用后必须补跑 Testcontainers |

## 6. 不允许退化的检查清单

- 不以 `DOUBLE`、`FLOAT`、`DECIMAL` 或 JavaScript `Number` 表达货币。
- 不以物理 `id`、`biz_id`、外键或泛化关系 ID 建模业务关系。
- 不把缺失行情、未结算期货损益、对账差异或导入歧义伪造成现金或成功状态。
- 不使用券商 API、自动调账、自动期货日结算、期权自动归零、期权卖空或 FX 换算。
- 不在小程序缓存中排队未同步账务写入。

## 7. 第一轮审查：金融账务、DDD 与数据一致性

**审查依据：** 已批准规格的 B-1、B-2、B-3、B-5、B-8、B-13、B-14 与 E-1--E-5，以及现有 `new_project` 的账户、分录和幂等实现。以下“修复”均指本计划已完成的修订；对应代码必须按 S0 的 TDD 次序落地后才可宣称符合规格。

| 编号 | 发现 | 风险 | 计划修复 | 验收证据 |
| --- | --- | --- | --- | --- |
| R1 | 当前实现会按币种预创建全局 `ASSET_INVESTMENT` 科目。 | 无法表达“现金账户 + 标的”维度的成本，且不符合 B-2 的 `INV:{cash_account_id}:{instrument_id}`。 | 在 3.1.2 改为首次开仓同事务懒创建；明确禁止 `ASSET_CLEARING`。 | 同一标的在两个现金账户开仓得到两个不同 `INV` 科目；未开仓没有 `INV`。 |
| R2 | 原计划 FIFO 排序写成了开仓日期/来源明细，而批准规则要求交易事实排序。 | 同日历史补录或重放可能改变已实现成本。 | 在 3.3.3--3.3.5 固定为 `(occurred_on, transaction_id, detail_no)`，并把余数归属纳入重放算法。 | 同日多笔交易任意重复重放的批次、成本和损益完全一致。 |
| R3 | 现金账户同名同币种的限制仅靠应用层无法抵抗并发；账户停用门禁也未被纳入实现序列。 | 可出现重复现金账户或停用后仍引用账户。 | 在 3.1.1、3.1.3 添加 V4 生成列唯一索引和锁内完整停用检查。 | 两个并发创建只有一个成功；不满足任一停用条件均不变更版本。 |
| R4 | 现有出金/转账路径未以账务事实重放验证余额。 | 可能写入负现金，违反 E-1。 | 在 3.1.3 把余额/保证金校验前置到 owner 锁内的不可变事实重放。 | 余额不足返回 `422 INSUFFICIENT_BALANCE`，零新增分录/审计/outbox。 |
| R5 | 当前幂等执行器与交易写入是分离边界，且计划原表述没有精确写明状态、事实、审计、outbox 和投影的提交次序。 | 失败或并发时可能产生“已成功响应但事实不完整”或重复事实。 | 在 3.1.4--3.1.5 固化为一条本地事务、一个 owner 锁和实际响应重放。 | 并发同键只产生一次事实；任一持久化故障后所有表均无该命令痕迹。 |

**第一轮结论：** 发现 5 项 P1 级实现/计划偏差，均已写入 S0--S1 的强制实施与验收条款；在 S0 测试先红、实现转绿前，不得开始现货交易命令。

## 8. 第二轮审查：接口、小程序、安全、运行与发布

**审查依据：** 当前 REST/幂等实现、OpenAPI、小程序请求层、本地 Compose 与已批准规格的 P-1、B-13、B-17、B-18、D-1--D-4、E-8、E-12。该轮不把“本地可编译”误认为微信可发布或云端可运行。

| 编号 | 发现 | 风险 | 计划修复 | 验收证据 |
| --- | --- | --- | --- | --- |
| R6 | 现有幂等表已预留 `response_status`，但更新逻辑把成功状态固定写为 `201`，且响应重放对象不携带状态。 | 后续 `200`、`202` 或业务响应升级会被错误重放。 | S0 3.1.5 规定存储实际 HTTP 状态与版本化响应体；契约测试覆盖非 `201` 的重放。 | 初次响应与同键重放的状态、头部白名单和 body 一致。 |
| R7 | DTO 的字符串类型并不能天然保证 JSON token 是字符串；小程序也必须避免以 JS 数值承载长整型金额。 | Jackson 可能宽松转换，或前端精度丢失后发出错误金额。 | 3.4.1 添加严格 token 反序列化；3.4.3 限定货币字段全程字符串；质量门禁加入边界契约测试。 | `666`（number）被拒绝，`"666"` 被接收；超过 JS 安全整数的金额仍逐字符一致。 |
| R8 | 现有小程序每次点击会新建幂等键，网络结果未知时没有“同一用户动作”的安全恢复语义。 | 用户手工重试可能变成第二笔账务写入。 | 3.4.5 固定原确认动作的 key，且仅允许用户明确点击后重试。 | 断网后手动重试只形成一条账务事实；无后台自动调用。 |
| R9 | 本地 MinIO 已有私有桶和本地加密配置，但应用尚未有对象存储适配器、预签名 POST 或对象校验链路。 | 不能把本地 Compose 误当成满足 B-17 的文件安全实现。 | S5 必须服务端生成短时、单文件、owner/file-ID 绑定的 POST policy，固定 bucket/key/大小/MIME/服务端加密上下文和不可变 `formData`；worker 读取后再验 SHA-256、大小/MIME/结构和扫描，扫描通过才复制证据对象并删除隔离副本。 | MinIO 集成测试篡改方向、对象键、表单、哈希或加密上下文均失败；相同内容两次上传仍有独立对象与 30 天生命周期。 |
| R10 | 当前 local 配置可使用 Mock/回环地址，不能直接作为微信体验版或生产环境配置。 | 可能以 HTTP、IP、local Mock 或占位密钥进入提审。 | S6 增加 production 启动配置校验：拒绝 local profile、回环/HTTP URL、空/示例密钥和未登记 HTTPS request/upload 域名；真实 `wx.login → code2Session → openid HMAC allowlist → opaque session`、隐私声明和真机证据任一缺失均阻断构建/提审。 | production 配置负例启动失败；真机回归与发布清单完整留档。 |
| R11 | 当前测试环境无 Docker daemon，数据库/Redis/MinIO 的真实事务、权限、加密和上传语义尚未验证。 | 可能在 Unit 通过后漏掉 Flyway、锁、容器和对象策略问题。 | 质量门禁保持 Testcontainers 标签与可重复入口；环境恢复后以 MySQL、Redis、MinIO、SQLite fixture 全部补跑，未通过不得宣称发布就绪。 | CI/本地容器报告明确区分 Unit 通过与 Integration 通过。 |

**第二轮结论：** 发现 6 项 P1/P2 交付偏差，均已映射到 S0、S1、S5、S6 的实施与验证条件。两轮审查没有发现需要新增的业务规则；可以按 S0 的测试先行顺序开始代码实现。

## 9. 计划自检

| 检查项 | 结果 |
| --- | --- |
| 已以当前代码而非历史描述盘点实现/缺口 | ✅ |
| 每个未完成 Phase 2 模块均有依赖顺序与交付证据 | ✅ |
| 金额、语义 ID、无外键、不可变账务规则被显式继承 | ✅ |
| Unit、Integration、E2E/Manual 的验证边界明确 | ✅ |
| 无新增业务 TBD；真实微信和云参数保持既有发布门禁 | ✅ |
