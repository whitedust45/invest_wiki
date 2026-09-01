# 微信小程序发布与云部署门禁

本清单是 `new_project/` 的发布前门禁。任一“阻断”项未完成时，不得提交体验版或正式版。

## 1. 已在代码中具备的保护

- 本地 Mock 只能在显式 `local` profile 下启用；没有 profile 时不会默认进入 local。
- 非 local 进程拒绝 Mock 认证、占位符密钥、`local-*` KMS 版本，以及 HTTP 或回环的服务端对象存储端点、客户端上传端点。
- 文件上传使用 owner/file ID 隔离的短时 POST；扫描、状态查询、证据对象与 30 天清理均为 server-side 行为。
- 云 Compose 模板只将 API 绑定到 `127.0.0.1`；反向代理、HTTPS 证书和微信合法域名必须在应用外提供。

## 2. 构建与容器验证（阻断）

在有 Docker daemon 的 CI 或发布主机执行：

```text
cd services/investment-api && mvn test -Pintegration
cd ../apps/miniprogram && npm run typecheck
docker build -t <IMMUTABLE_IMAGE_TAG> -f services/investment-api/Dockerfile services/investment-api
docker compose --env-file <PRODUCTION_SECRET_FILE> -f infra/docker-compose.cloud.example.yml config
```

必须保存以下证据：Flyway V1--V10 完整迁移、无外键/泛化 `biz_id` 的 schema gate、MySQL/Redis/MinIO/SQLite 集成结果、容器启动日志和 `/actuator/health` 响应。不得把 Unit 通过代替这些证据。

- V10 将早于该迁移、尚未提交的导入预览标记为 `EXPIRED`，以避免 MySQL JSON 规范化导致的校验和误确认；用户可基于同一份已扫描证据重新创建预览。

## 3. 真实微信与域名（阻断）

- `<WECHAT_APP_ID>`、`<WECHAT_APP_SECRET>` 与真实 `code2Session` 适配器已接入；本地 Mock 不得打入 production。
- 仅 allowlist 中管理员 openid 能获得会话；HMAC 密钥、AppSecret、会话密钥和数据库凭据位于受控 Secret 存储，不在 Git、镜像或小程序内。
- `<APP_PUBLIC_DOMAIN>` 与 `upload.<APP_PUBLIC_DOMAIN>` 均为 HTTPS，已在微信后台登记为 request/upload 合法域名；真机 `wx.login`、`wx.request`、`wx.uploadFile` 均已回归。
- 小程序隐私保护指引、30 天文件保留说明、主体/类目/备案与 HTTPS 证书材料齐备。

## 4. 数据与安全（阻断）

- 应用 MySQL 账号已在迁移前创建；`MYSQL_FLYWAY_USERNAME` 仅用于 Flyway，且具备对既有表执行最小权限 `GRANT` 的 `GRANT OPTION`。V9 迁移完成后，应用账号必须没有账本事实表的 `UPDATE` 或 `DELETE` 权限。
- `<PRODUCTION_MALWARE_SCANNER>` 已替换 local 结构性扫描器；失败必须保持文件不可导入。
- `<KMS_KEY_ID>`、最小权限对象存储 IAM、私有 bucket、30 天对象删除审计和数据库/对象存储备份恢复已演练。
- `<RPO>`、`<RTO>`、`<BACKUP_RETENTION_DAYS>`、`<LOG_RETENTION_DAYS>` 与告警接收人均已确认。
- 行情源许可证、缓存/展示/归因/频率限制证据已审核；未获授权的数据源不得在小程序展示。

## 5. 运行与回滚（阻断）

- 生产 `SPRING_PROFILES_ACTIVE=production`，并通过非 local 启动校验。
- 反向代理仅转发 HTTPS；API 容器不直接暴露公网页面端口。
- Flyway 迁移、应用版本与镜像 digest 已记录；回滚仅允许回退应用镜像，不回退已执行的数据库迁移。
- XXL-JOB 在 `<XXL_JOB_VERSION>`、许可使用方式、镜像来源、持久化库、管理员账号、备份策略和 `Asia/Shanghai` 凌晨 Cron 全部确认后才可接入生产；当前不以 Spring 本地定时器替代行情刷新任务。
