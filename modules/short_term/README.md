# 超短策略模块

通过 `registry.py` 注册独立策略。每个策略只能依赖通用数据接口或自己的执行适配器，不得依赖账本业务代码。

```bash
python3 modules/short_term/run.py
python3 modules/short_term/run.py brick --help
python3 modules/short_term/backtest_brick.py --start-date 2026-01-01 --end-date 2026-06-30
python3 invest.py short backtest-brick --start-date 2026-01-01 --end-date 2026-06-30
python3 invest.py short backtest-brick --start-date 2026-01-01 --end-date 2026-06-30 --workers 2
python3 invest.py short backtest-brick --start-date 2026-01-01 --end-date 2026-06-30 --refresh-cache
```

现有 `brick` 为通达信砖型图策略。实时报告将“双引擎共振”的正分 Top 3 单列为执行候选，原生或 Python 单端信号保留在观察总表中；硬过滤为零分的候选不会进入执行候选。当前评分权重为：砖型图强度 30、前一日 KDJ J 20、板块领先 15、黄白趋势 14、相对强度 10、流动性 6、低 J 十字星 5。新增策略时创建 `strategies/<strategy>.py`，再在 `registry.py` 注册即可。

`backtest_brick.py` 是独立的历史评分有效性回测：逐日选择概念板块前五，回放原生 `ZHUAN` 与 Python 严格信号的并集，只统计全局正分 Top 5，并把 Top 3 作为核心结果。报告写入 `data/tdx-brick-selector/backtests/<开始日期>_<结束日期>/report.md`，逐票明细写入同目录 `picks.csv`。

## A 股尾盘四策略（Windows + 通达信）

`tail` 是独立于砖型图的四个日线尾盘策略：`steady_momentum`、`trend_confirmation`、`macd_divergence` 和 `cup_handle_breakout`。它只产生候选或运行历史回测，不包含券商下单代码。

准备 UTF-8 文本股票池，例如 `symbols.txt`，每行一个通达信代码：

```text
000001.SZ
600000.SH
300750.SZ
```

在已安装并可运行通达信的 Windows 环境执行：

```bash
python invest.py short tail --strategy steady_momentum --symbols-file symbols.txt --tdx-path F:\new_tdx64
python invest.py short backtest-tail --strategy steady_momentum --symbols-file symbols.txt --start-date 2024-01-01 --end-date 2026-06-30 --tdx-path F:\new_tdx64
```

回测默认尾盘以收盘价近似买入、次日起可卖、最多三只、最长十日；佣金双边万分之一、卖出印花税万分之五、单边滑点万分之五，均可通过命令行参数覆盖。输出写入 `data/short-term/tail-backtests/<开始>_<结束>/<策略>/` 的 `trades.csv`、`report.json`、`report.md`。

Mac 只运行纯逻辑测试，不应在未安装通达信 TQ 的环境宣称已经完成真实历史回测。日线无法还原同日止盈与止损的先后顺序，程序一律按止损优先。

报告还会独立给出“双引擎共振组合”：只保留同日原生与 Python 都命中的候选，再重新评分排序并统计自己的 Top 1、Top 3、Top 5；该组合的逐票明细写入 `consensus-picks.csv`。

回测以信号日收盘价近似尾盘成交价，不模拟账户、手续费或完整退出；通达信接口不提供历史时点板块成分，因此当前板块成分会带来前视偏差，优先用于最近 3 至 6 个月的评分校准。

`--workers` 只并行纯 Python 的逐日评分；通达信 TQ 取数和原生公式保持单线程，避免共享客户端连接的线程安全风险。默认 `1`，Windows 建议先尝试 `--workers 2`；报告会列出各阶段耗时，若内存压力明显则改回 `--workers 1`。

回测会把通达信未复权日线缓存到 `data/tdx-brick-selector/backtest-cache.db`，首次运行拉取并写入，后续相同或重叠区间只补齐缺口；不会缓存原生公式结果或评分结果。报告的“日线缓存”表会展示缓存命中、实际 TQ 请求数与各自耗时。日线请求只取 `Open/High/Low/Close/Volume/Amount`，关闭缺口向后填充，并按单次最多 24,000 条记录自动缩小批量。需要重新下载指定区间时加 `--refresh-cache`；自定义缓存位置可用 `--cache-path <路径>`。
