# Sync Service

`services/sync` 是兼容入口；账本与同步服务的真实实现位于 `modules/ledger/local_service.py`。

目标架构：

```text
手机/电脑前端
→ /api/sync/*
→ 本同步服务
→ Gitee 私有数据仓库(JSON + commit history)
```

设计边界：

- 前端不保存、不传递 Gitee token
- Gitee token 只通过服务端环境变量读取
- 数据仍以 JSON 文件为主，不引入数据库
- 本地原型和公网部署使用同一组 API

本地配置从 `.env` 读取，字段示例见 `.env.example`。

## 本地运行

在项目根目录运行：

```bash
python3 modules/ledger/local_service.py
```

然后访问：

```text
http://127.0.0.1:8775/apps/dashboard/index.html
http://127.0.0.1:8775/apps/dashboard/index-desktop.html
```

初始化或刷新 Gitee 数据仓库基础 JSON：

```bash
python3 modules/ledger/local_service.py --init-gitee
```

当前同步 API：

```text
GET  /api/sync/health
GET  /api/sync/status
GET  /api/sync/data
GET  /api/sync/data/{domain}
POST /api/sync/data/{domain}
```

其中 `/api/sync/data*` 需要请求头：

```text
X-App-Key: <APP_ACCESS_KEY>
```

## Gitee 数据文件

`--init-gitee` 会在配置的 Gitee 私库中维护以下 JSON：

```text
ledger/current.json
dashboard/state.json
valuation/ic-im.json
positions/quotes.json
positions/history.json
meta/sync-meta.json
```

`dashboard/state.json` 是前端多端一致性的完整状态文件，包含 `ledger`、`positionValuations`、`history`、`valuation` 和 `historyView`。

## 前端自动同步策略

- 本地保存成功后，前端会 debounce 后自动推送 `dashboard/state.json`
- 自动推送前会先读取 Gitee 远端
- 如果远端与上次确认的同步基线一致，才允许自动覆盖远端
- 如果远端也发生变化，自动推送暂停，前端显示冲突选择面板
- 拉取远端覆盖本地时不会立刻反向自动推送
