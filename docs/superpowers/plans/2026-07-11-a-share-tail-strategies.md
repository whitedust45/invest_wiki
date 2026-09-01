# A 股尾盘短线四策略 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有短线模块中实现四个 A 股尾盘策略和 Windows + 通达信日线回测程序。

**Architecture:** `tail.py` 只负责纯指标、四种候选信号和实时扫描；`backtest_tail.py` 复用 `DailyBar` 与缓存，负责组合回放、费用和报告。策略通过同一个候选与退出接口接入组合引擎。

**Tech Stack:** Python 标准库、现有 TQ 接口、SQLite、CSV、JSON、Markdown、`unittest`。

## Global Constraints

- 用户提供 A 股 `--symbols-file`；任一交易日实际持仓严格不超过 3 只。
- 信号日尾盘按收盘价近似买入；买入后 T+1；最长持有 10 个交易日。
- 默认佣金为双边万分之一且免最低佣金；卖出印花税万分之五；单边滑点万分之五；全部可覆盖。
- 同一日止盈、止损同时触及时一律按止损；Mac 不跑真实通达信回测。
- 仅本地提交，绝不推送。

---

### Task 1: 纯策略模型、注册与测试

**Files:**
- Create: `modules/short_term/strategies/tail.py`
- Modify: `modules/short_term/strategies/__init__.py`
- Modify: `modules/short_term/registry.py`
- Create: `tools/dashboard/test_tail_strategies.py`

**Interfaces:**
- Consumes: `modules.short_term.strategies.brick.DailyBar`。
- Produces: `TailCandidate`, `TailStrategy`, `available_tail_strategies()`, `score_strategy()` 和 `tail.main()`。

- [ ] **Step 1: 写失败测试。**

```python
from modules.short_term.strategies.tail import available_tail_strategies, sma, ema

assert [item.strategy_id for item in available_tail_strategies()] == [
    "steady_momentum", "trend_confirmation", "macd_divergence", "cup_handle_breakout"
]
assert sma([1, 2, 3], 2) == [1.0, 1.5, 2.5]
assert round(ema([1, 2, 3], 2)[-1], 6) == 2.555556
```

- [ ] **Step 2: 执行失败测试。**

Run: `python3 tools/dashboard/test_tail_strategies.py`

Expected: 导入 `tail` 失败。

- [ ] **Step 3: 写最小实现。**

```python
@dataclass(frozen=True)
class TailCandidate:
    strategy_id: str
    symbol: str
    score: float
    metrics: dict[str, float]
    stop_loss_pct: float
    take_profit_pct: float
    max_holding_days: int
    exit_rule: str

@dataclass(frozen=True)
class TailStrategy:
    strategy_id: str
    display_name: str
    description: str
    score: Callable[[str, Sequence[DailyBar], Sequence[DailyBar] | None], TailCandidate | None]
```

实现 SMA、EMA、RSI、MACD、布林中轨、类夏普、量价波动、局部低点背驰、杯柄形态；数据不足均返回 `None`。实现设计规定的四个信号及止损、止盈、10 日上限，注册 `tail` 策略入口。

- [ ] **Step 4: 执行通过测试。**

Run: `python3 tools/dashboard/test_tail_strategies.py`

Expected: 指标、四策略注册、数据不足和候选上限测试通过。

- [ ] **Step 5: 本地提交。**

Run: `git add modules/short_term/strategies/tail.py modules/short_term/strategies/__init__.py modules/short_term/registry.py tools/dashboard/test_tail_strategies.py && git commit -m "feat: add tail strategy signals"`

### Task 2: 三仓日线回测器

**Files:**
- Create: `modules/short_term/backtest_tail.py`
- Modify: `invest.py`
- Modify: `tools/dashboard/test_tail_strategies.py`

**Interfaces:**
- Consumes: `TailCandidate`, `TailStrategy`, `BacktestBarCache`, `DailyBar`。
- Produces: `BacktestConfig`, `Position`, `Trade`, `execute_exit()`, `kelly_fraction()`, `run_backtest()` 和 `backtest_tail.main()`。

- [ ] **Step 1: 写失败测试。**

```python
from modules.short_term.backtest_tail import BacktestConfig, execute_exit, kelly_fraction

assert abs(kelly_fraction(0.6209, 1.81) - 0.4114) < 0.0002
trade = execute_exit(position, DailyBar("2026-01-02", 9, 11, 8, 10), BacktestConfig())
assert trade.reason == "stop_loss"
```

- [ ] **Step 2: 执行失败测试。**

Run: `python3 tools/dashboard/test_tail_strategies.py`

Expected: 导入 `backtest_tail` 失败。

- [ ] **Step 3: 写最小实现。**

```python
@dataclass(frozen=True)
class BacktestConfig:
    initial_cash: float = 100_000.0
    max_positions: int = 3
    commission_rate: float = 0.0001
    sell_stamp_tax_rate: float = 0.0005
    buy_slippage: float = 0.0005
    sell_slippage: float = 0.0005
```

实现 T+1、开盘越界、同日止损优先、单阈值触及、技术退出、最长持有退出、等权补足三仓、佣金/税/滑点、交易统计、权益曲线、最大回撤、胜率、盈亏比与凯利。复用 `load_tq`、`fetch_historical_bars` 与现有缓存；`--symbols-file` 空、日期无效、数据字段不足或 TQ 连接失败时给出中文错误。把 `invest.py short backtest-tail` 路由到新 CLI。

- [ ] **Step 4: 执行回归与帮助检查。**

Run: `python3 tools/dashboard/test_tail_strategies.py && python3 invest.py short backtest-tail --help`

Expected: 纯逻辑测试通过；帮助包含股票池、策略、日期和成本参数。

- [ ] **Step 5: 本地提交。**

Run: `git add modules/short_term/backtest_tail.py invest.py tools/dashboard/test_tail_strategies.py && git commit -m "feat: add tail strategy backtester"`

### Task 3: 报告、知识库与 Windows 说明

**Files:**
- Modify: `modules/short_term/README.md`
- Create: `knowledge/wiki/sources/2026-07-11-bigquant-steady-momentum.md`
- Create: `knowledge/wiki/sources/2026-07-11-bigquant-trend-timing.md`
- Create: `knowledge/wiki/sources/2026-07-11-bigquant-divergence-reversal.md`
- Create: `knowledge/wiki/sources/2026-07-11-bigquant-cup-handle.md`
- Create: `knowledge/wiki/portfolios/a-share-tail-short-term-strategy-suite.md`
- Modify: `knowledge/wiki/index.md`
- Modify: `knowledge/wiki/log.md`
- Modify: `tools/dashboard/test_tail_strategies.py`

**Interfaces:**
- Consumes: `BacktestResult` 的统计和逐笔交易。
- Produces: `trades.csv`、`report.json`、`report.md` 及可导航的 Wiki 页面。

- [ ] **Step 1: 写失败测试。**

```python
payload = build_report_payload(result)
assert payload["max_positions"] == 3
assert payload["source_performance_is_not_reproduction"] is True
assert "日线先后顺序" in render_report_markdown(payload)
```

- [ ] **Step 2: 执行失败测试。**

Run: `python3 tools/dashboard/test_tail_strategies.py`

Expected: 报告字段或披露文本断言失败。

- [ ] **Step 3: 写最小实现和文档。**

在 `data/short-term/tail-backtests/<start>_<end>/<strategy>/` 写入三种产物。四份来源页记录真实 URL、原始胜率/盈亏比/凯利和改造差异；总策略页标记 `draft`，说明待 Windows + 通达信复测。README 写出股票池逐行代码格式、前置条件、扫描命令、回测命令与 Mac 未实跑边界，并更新 Wiki 索引与日志。

- [ ] **Step 4: 执行完整验证。**

Run: `python3 tools/dashboard/test_tail_strategies.py && python3 -m compileall modules/short_term invest.py && git diff --check`

Expected: 全部通过；没有语法或空白错误。

- [ ] **Step 5: 本地提交。**

Run: `git add modules/short_term/README.md knowledge/wiki/sources knowledge/wiki/portfolios/a-share-tail-short-term-strategy-suite.md knowledge/wiki/index.md knowledge/wiki/log.md tools/dashboard/test_tail_strategies.py && git commit -m "docs: add tail strategy research"`

### Task 4: 完成审计

- [ ] **Step 1: 检查目标覆盖。**

确认恰有四个策略、持仓上限为三、最长十日、四个真实来源和正凯利、三种回测产物、Windows 指南、无下单代码、无 Mac 真实回测声明。

- [ ] **Step 2: 最终检查。**

Run: `git status --short && git log -4 --oneline`

Expected: 工作区干净且本地提交可追溯。
