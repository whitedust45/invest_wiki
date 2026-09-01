# 个人投资小程序（新实现）

本目录承载新的可发布实现；原有 Dashboard/Python/SQLite 仅作为迁移参考，不作为新系统运行时依赖。

## 当前实现边界

已实现 Java 21、Spring Boot 3.5.14、Flyway、MyBatis、Redis 会话、local Mock 微信登录，以及面向微信小程序的现金账户、资金流水、股票/ETF FIFO、IC/IM 多头期货、买入型期权、手工估值、手工对账、历史数据受控上传、账本筛选/导出与加密快照恢复。

历史文件仅可由小程序选择 JSON/SQLite 后通过服务端签发的预签名 POST 写入 owner 隔离对象键；服务端随后以有租约的 worker 重新校验大小、MIME、SHA-256、owner/file 元数据并扫描，再复制为加密证据对象。local profile 使用结构性扫描器；非 local 环境必须接入真实恶意文件扫描器才会启动。原件与证据按各自创建时间满 30 天删除，保留哈希与审计摘要。

JSON/SQLite 解析器、逐行 dry-run 映射、checksum 确认与原子账务提交均已实现。已确认的口径是：手工 `market_value_cent` 属于“用户 + 标的”且只在组合总览中计一次；旧 Dashboard 的 `buy`/`sell`/`put.price` 一律按原币种十进制报价精确乘以 100 进入 `unit_price_cent`，不受 `amountUnit` 影响；无法验证两腿的旧 `ic/roll` 只导入其费用，并标记 `ROLL_FEE_ONLY`。完整边界见 `../docs/designs/phase2-ledger-spec.md`。

账本 JSON/CSV 导出完全按当前 owner 隔离，金额保持原币种最小单位字符串，每次导出均被审计。JSON 导出可作为加密云端快照正文；快照的对象存储地址永不暴露给小程序，下载与恢复前由服务端校验 owner、工件元数据和 SHA-256。恢复不是数据库回滚：它只允许对没有账户且没有交易的空账本工作区，原子写入拥有新业务 ID 的 `IMPORT` 事实；任何已有账本都会以 HTTP 409 拒绝。完整等价矩阵和安全边界见 `../docs/designs/dashboard-miniprogram-feature-equivalence.md`。

## 本地启动

1. 复制 `.env.example` 为 `.env`，填入仅用于本机的随机密钥。
2. 在 `infra/` 运行 `docker-compose --env-file ../.env -f docker-compose.local.yml up -d`。（若本机安装的是 Docker Compose v2，也可等价使用 `docker compose`。）
3. 在 `services/investment-api/` 运行 `zsh -c 'set -a; source ../../.env; set +a; exec mvn spring-boot:run -Dspring-boot.run.profiles=local'`。Maven 不会自行读取 `.env`，该命令只把变量载入当前进程且不输出密钥。`local` 不再是默认 profile，避免云端漏配 profile 时误启用本地 Mock 登录。
4. 微信开发者工具导入 `apps/miniprogram/`，并把 `config.ts` 的 API 地址改为本机可访问地址。

首次本地登录使用 `.env` 中的 `LOCAL_MOCK_LOGIN_CODE` 和 `BOOTSTRAP_ENROLLMENT_SECRET`。这些值不能提交、不能用于任何非 local 环境。

## 验证

- 后端单元测试：在 `services/investment-api/` 执行 `mvn test`。
- 小程序类型检查：在 `apps/miniprogram/` 执行 `npm run typecheck`。
- MySQL、Redis、MinIO 的集成测试依赖本机 Docker daemon；正式发布前还必须完成真实微信 `code2Session`、已备案 HTTPS 域名、生产恶意文件扫描、对象存储 IAM/KMS、隐私声明和真机回归门禁。

## 云服务器部署占位工件

`services/investment-api/Dockerfile` 生成非 root 的 Java 21 运行镜像；`infra/docker-compose.cloud.example.yml` 只连接外部受管 MySQL、Redis 和对象存储，并只把 API 端口绑定到云服务器回环地址，交由 HTTPS 反向代理公开。对象存储配置区分后端可达的 `OBJECT_STORAGE_ENDPOINT` 与小程序直传签名表单使用的 `OBJECT_STORAGE_UPLOAD_ENDPOINT`；后者必须是已备案、已配置到微信小程序后台的公共 HTTPS 域名，绝不能返回容器内部主机名。

复制云部署示例所需环境变量后，再通过不可变镜像 tag 或 digest 部署。不得把 `.env`、数据库密码、微信 AppSecret 或对象存储密钥写入镜像或 Git。此示例不会绕过发布门禁：production profile 在真实微信适配器、生产恶意文件扫描、注册的 HTTPS 域名及相关凭据尚未接入前必须失败关闭。

完整的构建、真机、数据安全和云发布阻断条件见 `infra/RELEASE_GATE.md`。
