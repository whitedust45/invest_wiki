# 业务模块

项目按业务职责划分为三个模块，依赖方向为 `ledger -> market_data <- short_term`。

- `ledger/`：长期投资账本、本地镜像、Gitee 同步和仪表盘服务组合。
- `market_data/`：行情、估值、数据源优先级、长期 SQLite 事实库和投影接口。
- `short_term/`：短线策略注册表、通达信适配、策略实现和定时任务。

`apps/dashboard/` 只保存账本的前端资源和读取投影；`tools/dashboard/` 与 `services/sync/` 保留兼容入口，不再承载核心实现。

日常使用只需项目根目录的统一入口：

```bash
python3 invest.py ledger
python3 invest.py market quotes 000858 600036 --history-days 260
python3 invest.py market valuation
python3 invest.py short
```

Windows 可用 `./invest.ps1 ledger`、`./invest.ps1 short brick --help`。在真正执行前追加 `--dry-run` 可查看实际模块命令。
