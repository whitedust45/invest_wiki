# 公共数据接口

`api.py` 是供账本和短线模块使用的应用门面，提供观察清单、行情刷新、后台任务与 JSON 投影读取能力。`providers.py`、`store.py`、`quotes.py`、`valuation.py` 是其内部实现。

运行行情更新：

```bash
python3 modules/market_data/quotes.py 000858 600036 --history-days 260
python3 modules/market_data/valuation.py
```

数据事实库仍为 `apps/dashboard/data/market-data.db`；前端 JSON 仅是兼容投影，不是事实源。
