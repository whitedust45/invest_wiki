# Phase 1：本地基础骨架可执行规格

**状态：** 已批准并完成 Phase 1 基础实现；真实微信、云部署与后续业务限界上下文仍未实现。

**依据：** [完整实施规划](wechat-miniprogram-java-ddd-implementation-plan.md)、[业务 ID 命名规范](business-id-naming-v2.md)、[最终 MySQL Schema](mysql-ddd-schema-v1.sql)。

## 1. 接口背景与功能

**背景：** 首期是个人私有投资记录小程序。需要先建立一个不依赖真实微信、云服务器或行情供应商的本地运行闭环，作为后续账本、行情与小程序页面的安全底座。

**功能：** 建立 Java 21/Spring Boot DDD 模块化单体、小程序登录/总览壳、MySQL/Flyway、Redis 会话、管理员 allowlist、健康检查与当前用户查询。

**使用者：** 本地微信开发者工具中的个人管理员；后续 Java 领域模块；本地自动化测试。

**交互模式：** 小程序 HTTPS/HTTP REST 调用 Java API；本地 profile 由 Mock 微信身份适配器替代真实 `code2Session`。

**本切片不包含：** 账本写入、行情抓取、文件上传、XXL-JOB、MinIO、真实微信 `code2Session`、云部署、MQ/RPC/TCC/ES。

## 2. 接口定义

### 2.1 公共约定

- API 前缀为 `/api/v1`；JSON 采用 camelCase。
- 领域业务 ID 使用 ULID 字符串；物理自增 `id` 永不出现在 API。
- 金额字段暂不在本切片中出现。后续出现时必须是 `*_cent` 十进制字符串及 `currency`。
- 成功响应使用 HTTP 2xx；错误采用 RFC 7807 风格 JSON，固定字段为 `code`、`message`、`traceId`、`details`。

### 2.2 `POST /api/v1/auth/wechat/login`

本地 profile 仅接受由 Mock 适配器解释的开发 code；非本地 profile 必须调用真实适配器，真实微信契约在本切片不启用。

```yaml
request:
  code: string # 必填，1..256 字符；本地使用 LOCAL_MOCK_LOGIN_CODE
  bootstrapEnrollmentSecret: string # 可选；仅首次管理员绑定时需要
response:
  accessToken: string # 不透明随机 token，不能是微信 session_key
  expiresAt: string # RFC 3339 UTC 时间
  user:
    userId: string # ULID
    role: ADMIN
```

| 字段 | 必填 | 约束 | 说明 |
| --- | --- | --- | --- |
| `code` | 是 | 非空、最大 256 字符 | 传递给身份适配器，不记录明文日志。 |
| `bootstrapEnrollmentSecret` | 否 | 最大 512 字符 | 仅首次绑定管理员时校验；日志中永不记录。 |
| `accessToken` | 响应必有 | 不透明、不可推导用户 ID | Redis 仅存 token HMAC 与会话元数据。 |

### 2.3 `GET /api/v1/me`

```yaml
requestHeaders:
  Authorization: Bearer <accessToken>
response:
  userId: string
  role: ADMIN
  sessionExpiresAt: string
```

### 2.4 `GET /actuator/health`

- 无认证。
- 仅返回服务与必要依赖健康状态；不得包含密钥、数据库 URL 或用户信息。
- Spring Boot Actuator 的细节响应仅在 local profile 开放；非 local 的暴露策略为 `TBD-1`。

## 3. 业务规则与边界条件

### 3.1 参数校验

- P-1：`code` 缺失、空白或超长 → 返回 `400 AUTH_CODE_INVALID`。
- P-2：`bootstrapEnrollmentSecret` 超长 → 返回 `400 BOOTSTRAP_SECRET_INVALID`。
- P-3：`Authorization` 缺失、格式不是 `Bearer <token>`、token 为空 → 返回 `401 SESSION_INVALID`。

### 3.2 核心规则

- B-1：local profile 的 `code` 等于 `LOCAL_MOCK_LOGIN_CODE` → Mock 适配器返回 `LOCAL_MOCK_OPENID`，且该值仅供本地使用。
- B-2：首次管理员绑定仅在数据库不存在有效管理员时允许；请求必须同时通过 allowlist/配置校验与一次性 enrollment secret 校验 → 创建 `iam_user`、`iam_wechat_identity`、审计记录和 Redis 会话。
- B-3：已有管理员再次登录 → 不重复创建身份；刷新/创建一个新会话并追加登录审计。
- B-4：非 allowlist 身份、失效用户或错误 enrollment secret → 拒绝，且只追加失败审计，不创建会话。
- B-5：`GET /me` 只从 Redis 已验证会话读取当前 `userId` 与角色；不得接受客户端提交的用户 ID。
- B-6：会话在 Redis 中仅保存 token HMAC、用户 ID、角色、闲置/绝对过期时间和 permission version；不得保存微信 `session_key`、明文 token 或 AppSecret。体验版/正式版会话为 30 分钟无操作失效、8 小时绝对失效；登出、allowlist/权限变更与密钥轮换立即失效该用户全部会话。local profile 可使用独立短期开发参数，但不得进入发布构建。
- B-7：每次写入业务身份和审计使用同一 MySQL 本地事务；会话只在提交成功后写 Redis。

### 3.3 边界条件

- E-1：管理员已存在时再次携带 enrollment secret → 正常登录，忽略该 secret，不允许重置已有管理员。
- E-2：Redis 不可用 → 登录和 `/me` 返回 `503 AUTH_SESSION_STORE_UNAVAILABLE`；不得回退为匿名访问或 MySQL token 表。
- E-3：MySQL 不可用 → 登录返回 `503 DEPENDENCY_UNAVAILABLE`；健康检查标为不健康。
- E-4：会话过期、被删除或 permission version 不匹配 → 返回 `401 SESSION_EXPIRED`。
- E-5：同一首次绑定请求并发到达 → 只能成功创建一个管理员；另一个请求返回可重试的 `409 BOOTSTRAP_CONFLICT` 或读取已存在管理员后按 allowlist 规则拒绝，具体响应以事务唯一约束为准。

### 3.4 降级策略

- D-1：真实微信适配器未配置或外部调用失败 → 本地 profile 可使用 Mock；非 local profile 返回 `503 WECHAT_AUTH_UNAVAILABLE`，不得采用 Mock。
- D-2：数据库或 Redis 依赖不健康 → 服务不接受认证或私有读写，仅保留最小健康检查。

### 3.5 时序与事务

- T-1：验证身份适配器结果 → 读取/创建用户与微信身份 → 追加审计，必须在创建 Redis 会话之前完成。
- T-2：MySQL 事务失败 → 不写 Redis 会话。
- T-3：Redis 写会话失败 → 返回 `503`；MySQL 中允许保留成功/失败登录审计，但不得把该次登录标记为可用会话。
- T-4：Bootstrap 状态只能从“无管理员”变为“已有管理员”；不得通过 API 回退。

⚠️ **AI 风险提示**：

- [R-1] `openid` allowlist 的存储形式与首次 enrollment 流程尚未选择。→ 本地切片建议用环境变量 HMAC 值和 `BOOTSTRAP_ENROLLMENT_SECRET`，真实环境改为密钥服务；见 `TBD-2`。
- [R-2] 真实微信 `code2Session` 的准确字段、超时、频率限制与错误码尚未获得官方配置。→ 不在本切片调用，真实适配器只能在契约确认后启用；见 `TBD-3`。

## 4. Code 映射

| code | message | 触发规则 | 是否可重试 |
| --- | --- | --- | --- |
| `OK` | success | B-1、B-2、B-3、B-5、E-1 | 否 |
| `AUTH_CODE_INVALID` | 登录 code 无效 | P-1 | 否 |
| `BOOTSTRAP_SECRET_INVALID` | 初始化密钥无效 | P-2、B-4 | 否 |
| `SESSION_INVALID` | 会话格式无效 | P-3 | 否 |
| `SESSION_EXPIRED` | 会话失效 | E-4 | 是，重新登录 |
| `NOT_ADMIN` | 身份不在管理员范围 | B-4 | 否 |
| `BOOTSTRAP_CONFLICT` | 首次绑定并发冲突 | E-5、T-4 | 是，重新读取后登录 |
| `AUTH_SESSION_STORE_UNAVAILABLE` | 会话存储不可用 | E-2、D-2、T-3 | 是 |
| `DEPENDENCY_UNAVAILABLE` | 必要依赖不可用 | E-3、D-2 | 是 |
| `WECHAT_AUTH_UNAVAILABLE` | 真实微信认证不可用 | D-1 | 是 |

## 5. 接口 I/O 示例

### 5.1 本地首次管理员绑定

```http
POST /api/v1/auth/wechat/login
Content-Type: application/json

{"code":"local-admin-code","bootstrapEnrollmentSecret":"local-bootstrap-secret"}
```

```json
{
  "accessToken": "opaque-session-token",
  "expiresAt": "2026-07-27T12:00:00Z",
  "user": {"userId": "01J4K3W8N8N4G2T9R7Q6Y5X4Z3", "role": "ADMIN"}
}
```

### 5.2 无效 code

```http
POST /api/v1/auth/wechat/login
Content-Type: application/json

{"code":""}
```

```json
{
  "code": "AUTH_CODE_INVALID",
  "message": "登录 code 无效",
  "traceId": "01J4K3W8N8N4G2T9R7Q6Y5X4Z4",
  "details": []
}
```

### 5.3 查询当前用户

```http
GET /api/v1/me
Authorization: Bearer opaque-session-token
```

```json
{
  "userId": "01J4K3W8N8N4G2T9R7Q6Y5X4Z3",
  "role": "ADMIN",
  "sessionExpiresAt": "2026-07-27T12:00:00Z"
}
```

### 5.4 Redis 不可用

```json
{
  "code": "AUTH_SESSION_STORE_UNAVAILABLE",
  "message": "会话服务暂不可用",
  "traceId": "01J4K3W8N8N4G2T9R7Q6Y5X4Z5",
  "details": []
}
```

## 6. 外部依赖行为约定

### 6.1 MySQL 8.4

- 用途：Flyway V1/V2 迁移、身份、审计数据。
- 接口：JDBC；连接 URL、账号、密码只通过环境变量注入。
- 一致性：B-2、B-3、B-4 的持久化操作在一个本地事务内。
- 失败：连接池不可用或事务失败返回 `DEPENDENCY_UNAVAILABLE`；不会写 Redis 会话。
- 本地验证：Spring Boot 3.5.14 受管的 Flyway 11.7.2 已在 MySQL 8.4.10 上实际执行 V1/V2；Testcontainers 架构门禁已实现，但本机缺少 Docker socket，尚待 CI 或可用 Docker 环境执行。Flyway 11.7.2 会对 MySQL 8.4 打出未测试版本告警，生产镜像冻结前必须完成 `<TBD_FLYWAY_MYSQL_8_4_COMPATIBILITY_DECISION>`。

### 6.2 Redis

- 用途：不透明会话和后续缓存；本切片不将任何 session 回退到 MySQL。
- 接口：Spring Data Redis；地址、密码和 TLS 配置使用环境变量。
- 一致性：会话是事务提交后的副作用；失败时登录失败并保留审计。
- 失败：遵循 E-2/T-3。

### 6.3 微信 `code2Session`

- 状态：本切片**不实际调用**，以 interface port 与 local Mock adapter 预留。
- 文档链接、AppID、AppSecret、完整入参/出参、错误码、频率、超时、重试和生产端点：`TBD-3`。
- 失败策略：生产 profile 的真实适配器失败即返回 `WECHAT_AUTH_UNAVAILABLE`；严禁 Mock 回退。

## 7. 动态内容生成规则

本切片无策略建议、行情、报表或文件生成。小程序总览只展示已认证用户状态与“账本、行情、策略、报表待接入”的静态空状态；不得伪造资产、收益或行情数字。

## 8. 性能与安全约束

### 性能

- 具体 QPS、P95/P99 和连接池大小均为 `TBD-4`；会话 TTL 使用 B-6 的发布基线，本地只验证正确性，不将临时值视为生产容量结论。
- `/me` 不访问 MySQL 业务表；只在必要时校验 Redis 中的 permission version。

### 安全

- `WECHAT_APP_SECRET`、Redis/MySQL 密码、bootstrap secret 不进入 Git、小程序包、日志、错误响应或测试 fixture。
- local Mock code 和 bootstrap secret 只能由 `.env` 注入；production profile 必须拒绝 Mock adapter。
- API DTO 不得接收用户 ID、角色、openid 或 session_key 作为可信身份来源。
- 所有日志使用 request/trace ID；登录失败日志仅记录原因代码和脱敏身份哈希。
- 登录与会话刷新必须按 IP、openid HMAC 与会话三层限流；拒绝返回 `429 RATE_LIMITED`，不得由小程序静默重试。

### 测试边界声明

- 本地可保证：输入校验、bootstrap 状态机、事务顺序、Redis 不可用拒绝、会话验证、DDD 依赖方向、Flyway schema lint。
- Integration：MySQL/Redis 实际语义、Flyway、容器 Compose 启动。
- E2E/Manual：真实微信登录、开发者工具 AppID、真机合法域名、云密钥与网络。

## 9. 测试矩阵

| # | 测试场景 | 对应规则 | 测试类型 | 本地可跑 | 验证层级 | 验收证据 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 空/超长 code | P-1 | Controller unit | 是 | Unit | JUnit 输出 | `400 AUTH_CODE_INVALID`。 |
| 2 | 非法 bootstrap secret | P-2、B-4 | Application unit | 是 | Unit | JUnit 输出 | 不创建用户与会话，追加失败审计。 |
| 3 | 缺失/非法 Bearer | P-3 | Security unit | 是 | Unit | JUnit 输出 | `401 SESSION_INVALID`。 |
| 4 | 首次绑定成功 | B-1、B-2、T-1、T-2 | Repository integration | 是 | Integration | Testcontainers MySQL/Redis 输出 | 一个用户、一个身份、审计和会话；无明文 token。 |
| 5 | 已有管理员重复登录 | B-3、E-1 | Integration | 是 | Integration | Testcontainers 输出 | 不重复创建身份，产生新会话与审计。 |
| 6 | 非管理员登录 | B-4 | Integration | 是 | Integration | Testcontainers 输出 | `403 NOT_ADMIN`，无会话。 |
| 7 | `/me` 使用 Redis 会话 | B-5、B-6 | Integration | 是 | Integration | Testcontainers Redis 输出 | 仅返回当前管理员视图。 |
| 8 | MySQL 事务失败 | B-7、T-2 | Integration | 是 | Integration | 事务回滚断言 | Redis 无新会话。 |
| 9 | Redis 不可用 | E-2、D-2、T-3 | Integration | 是 | Integration | 故障注入输出 | `503 AUTH_SESSION_STORE_UNAVAILABLE`，不匿名降级。 |
| 10 | 会话过期/版本不符 | E-4 | Integration | 是 | Integration | Redis fixture 输出 | `401 SESSION_EXPIRED`。 |
| 11 | 并发首次绑定 | E-5、T-4 | Integration | 是 | Integration | 并发测试输出 | 最多一个有效管理员。 |
| 12 | production profile 拒绝 Mock | D-1 | Unit | 是 | Unit | profile 测试输出 | `503 WECHAT_AUTH_UNAVAILABLE` 或启动时配置失败。 |
| 13 | 最终 schema | B-7 | Repository integration | 是 | Integration | Testcontainers MySQL 输出 | 38 表、无含 `biz` 字段/索引、无外键、`*_cent BIGINT`。 |
| 14 | 真实微信与真机登录 | D-1、T-1 | 手工验收 | 否 | E2E/Manual | 真机截图与脱敏日志 | 仅合法管理员可登录。 |

## TBD 跟踪表

| # | 位置 | 待定问题 | 影响范围 | 建议默认值 | 状态 |
| --- | --- | --- | --- | --- | --- |
| TBD-1 | 2.4 | 非 local Actuator 暴露策略 | 发布安全 | 仅 `/actuator/health`，其余禁止公网暴露 | 发布前确认；不阻断本地。 |
| TBD-2 | 3.5 | allowlist 的生产存储与密钥轮换 | 管理员认证 | 密钥服务保存 `openid_hmac` allowlist，应用只读 | 发布前确认；不阻断 Mock。 |
| TBD-3 | 6.3 | 微信真实接口契约与参数 | 真实登录 | 先由官方文档/控制台确认，再启用真实 adapter | 明确不在本切片实现。 |
| TBD-4 | 8 | 容量、连接池、限流和监控阈值 | 性能与发布 | 依据真实设备和云资源压测确定；会话 TTL 固定为 B-6 | 不阻断本地正确性测试。 |

## Spec 自检

| # | 检查项 | 状态 |
| --- | --- | --- |
| 1 | 规则均有 Code 映射 | ✅ |
| 2 | 规则均有 I/O 或测试场景 | ✅ |
| 3 | 已定义依赖失败策略 | ✅ |
| 4 | 测试矩阵覆盖参数、并发、状态与降级 | ✅ |
| 5 | 已覆盖空值、依赖故障和并发 | ✅ |
| 6 | 风险已列出并隔离为不调用的真实链路 | ✅ |
| 7 | 所有 TBD 均被用户已授权的占位符策略限定，且不进入本地 Mock 运行路径 | ✅ |
| 8 | 已区分本地、集成与真实链路验收 | ✅ |
| 9 | 已定义事务与 Bootstrap 状态机规则 | ✅ |
| 10 | 未确认的微信下游不进入本切片，完整契约显式标为 TBD | ✅ |
