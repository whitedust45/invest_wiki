# 长期投资账本

账本以前端 `apps/dashboard/` 为交互层，以 `ledger.db` 追加快照为本地镜像，并通过本模块的本地服务提供账本、同步和公共数据接口组合。

```bash
python3 modules/ledger/local_service.py
```

账本不直接访问行情提供者或市场 SQLite 表，只使用 `modules.market_data` 的公共接口。
