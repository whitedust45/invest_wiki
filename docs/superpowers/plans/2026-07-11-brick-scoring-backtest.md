# 砖型图连续评分与历史回测 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将砖型图实盘评分改为已确认的连续模型，并新增可选择日期区间、双引擎历史回放、每日全局 Top 5 评价的独立回测脚本。

**Architecture:** `modules/short_term/strategies/brick.py` 继续作为实盘与回测共享的唯一评分实现；新增 `modules/short_term/backtest_brick.py` 负责历史 TQ 取数、按日期重放、未来收益统计和 Markdown/CSV 输出。`invest.py` 仅增加快捷路由，不复制业务逻辑。

**Tech Stack:** Python 3 标准库、unittest、通达信 `tqcenter` 本地 API、现有短线策略模块。

## Global Constraints

- 不新增第三方依赖，不扫描回测日当前前五概念板块之外的股票。
- 总基础权重固定为 100；硬过滤、风险扣分和板内排名上限保持独立。
- 原生 `ZHUAN` 与 Python 严格信号按日期取并集；原生批次失败时保留 Python 并标记部分完成。
- 每日只统计全局正分 Top 5，Top 3 是核心展示；0 分候选不占名额。
- 板块成员使用回测运行时当前成分，报告显著披露前视偏差。
- Mac 只运行离线测试；真实 TQ 历史回放留给 Windows 验证。

---

### Task 1: 连续评分成为实盘与回测共享模型

**Files:**
- Modify: `modules/short_term/strategies/brick.py`
- Modify: `tools/dashboard/test_tdx_brick_selector.py`

**Interfaces:**
- Produces: `linear_factor_score(value, bad, good, weight) -> float`
- Produces: `b1_trend_component_scores(...) -> dict[str, float]`
- Produces: `previous_kdj_j_score(previous_j) -> float`
- Preserves: `b1_trend_metrics`、`previous_kdj_j_metrics`、`previous_kdj_doji_metrics` public behavior shape.

- [ ] **Step 1: Write failing scoring tests**

Add tests asserting exact weights and continuous boundaries:

```python
self.assertEqual(sum(selector.FACTOR_WEIGHTS.values()), 100.0)
self.assertEqual(selector.FACTOR_WEIGHTS["brick_strength"], 28.0)
self.assertEqual(linear_factor_score(0.0, -1.0, 1.0, 8.0), 4.0)
self.assertEqual(previous_kdj_j_score(0.0), 15.0)
self.assertEqual(previous_kdj_j_score(6.0), 13.5)
self.assertEqual(previous_kdj_j_score(12.0), 12.0)
self.assertEqual(previous_kdj_j_score(18.0), 8.0)
self.assertEqual(previous_kdj_j_score(24.0), 4.0)
self.assertEqual(previous_kdj_j_score(30.0), 0.0)
self.assertEqual(previous_kdj_doji_metrics(doji_bars, 11.999)["score"], 5.0)
self.assertEqual(previous_kdj_doji_metrics(doji_bars, 12.0)["score"], 0.0)
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `python3 -m unittest tools.dashboard.test_tdx_brick_selector.TdxBrickSelectorTests -v`

Expected: FAIL because the new helpers and weights are absent.

- [ ] **Step 3: Implement continuous scores**

Use the approved weights and formulas:

```python
FACTOR_WEIGHTS = {
    "board_leadership": 12.0,
    "relative_strength": 10.0,
    "liquidity": 13.0,
    "tail_structure": 0.0,
    "brick_strength": 28.0,
    "white_yellow_trend": 17.0,
    "previous_kdj_j": 15.0,
    "previous_kdj_doji": 5.0,
}

def linear_factor_score(value, bad, good, weight):
    if not all(math.isfinite(item) for item in (value, bad, good, weight)) or good <= bad:
        return 0.0
    return weight * min(1.0, max(0.0, (value - bad) / (good - bad)))

def previous_kdj_j_score(previous_j):
    if previous_j <= 0:
        return 15.0
    if previous_j <= 12:
        return 15.0 - 3.0 * previous_j / 12.0
    if previous_j < 30:
        return 12.0 * (30.0 - previous_j) / 18.0
    return 0.0
```

`b1_trend_metrics` computes gap, white slope, yellow slope and close gap percentages, then calls a pure component helper with `(8, 4, 3, 2)` weights and `(-1,1)`, `(-0.5,0.5)`, `(-0.3,0.3)`, `(-1,1)` ranges. Doji score is exactly 5 only for body `<=1.5%` and `J<12`.

- [ ] **Step 4: Run selector tests**

Run: `python3 -m unittest tools/dashboard/test_tdx_brick_selector.py -v`

Expected: all selector tests PASS.

- [ ] **Step 5: Commit shared scoring**

```bash
git add modules/short_term/strategies/brick.py tools/dashboard/test_tdx_brick_selector.py
git commit -m "feat: make brick factor scoring continuous"
```

### Task 2: Build pure historical replay and outcome primitives

**Files:**
- Create: `modules/short_term/backtest_brick.py`
- Create: `tools/dashboard/test_tdx_brick_backtest.py`

**Interfaces:**
- Produces: `extract_historical_formula_matches(raw, candidates) -> dict[str, set[str]]`
- Produces: `historical_python_signals(stock_bars) -> tuple[dict[str, set[str]], dict[tuple[str, str], BrickSignal]]`
- Produces: `forward_outcomes(bars, signal_index) -> dict[str, float | bool | None]`
- Produces: `select_top_positive(rows, limit=5) -> list[dict[str, Any]]`
- Produces: `summarize_cohort(picks, top_n) -> dict[str, Any]`

- [ ] **Step 1: Write failing pure backtest tests**

Cover dated native values, source union, positive Top 5 and missing future data:

```python
raw = {
    "000001.SZ": {"XG": [{"Date": "20260701", "Value": "0"}, {"Date": "20260702", "Value": "1"}]},
    "600000.SH": {"XG": [{"Date": "20260701", "Value": "1"}]},
    "ErrorId": "0",
}
self.assertEqual(
    extract_historical_formula_matches(raw, ["000001.SZ", "600000.SH"]),
    {"2026-07-01": {"600000.SH"}, "2026-07-02": {"000001.SZ"}},
)
self.assertEqual([row["symbol"] for row in select_top_positive(rows, 5)], expected_top_five)
self.assertIsNone(forward_outcomes(two_bars, 1)["return_1d"])
```

- [ ] **Step 2: Run focused backtest tests and verify failure**

Run: `python3 -m unittest tools/dashboard/test_tdx_brick_backtest.py -v`

Expected: FAIL because `modules.short_term.backtest_brick` does not exist.

- [ ] **Step 3: Implement pure replay primitives**

Normalize formula dates through the existing brick date parser, accept both `{Date, Value}` entries and scalar/list fallback, and only record nonzero XG values. Precompute each stock's brick series once and map brick index `i` to bar index `i+3`; strict signals use the latest three brick values ending on that date.

Forward outcomes use:

```python
result[f"return_{horizon}d"] = (
    bars[index + horizon].close / bars[index].close - 1
    if index + horizon < len(bars) else None
)
result["hit_plus_3pct_next_day"] = bars[index + 1].high >= bars[index].close * 1.03
result["hit_minus_3pct_next_day"] = bars[index + 1].low <= bars[index].close * 0.97
```

Cohort summaries first average valid rows within each date and rank `<=top_n`, then compute mean, median and positive ratio across dates. Touch rates remain stock-sample rates.

- [ ] **Step 4: Run pure backtest tests**

Run: `python3 -m unittest tools/dashboard/test_tdx_brick_backtest.py -v`

Expected: all pure backtest tests PASS.

- [ ] **Step 5: Commit replay primitives**

```bash
git add modules/short_term/backtest_brick.py tools/dashboard/test_tdx_brick_backtest.py
git commit -m "feat: add brick historical replay primitives"
```

### Task 3: Add TQ historical orchestration and reports

**Files:**
- Modify: `modules/short_term/backtest_brick.py`
- Modify: `tools/dashboard/test_tdx_brick_backtest.py`

**Interfaces:**
- Produces: `fetch_historical_bars(tq, symbols, start_date, end_date, batch_size) -> tuple[dict[str, list[DailyBar]], list[str]]`
- Produces: `run_native_history(...) -> tuple[dict[str, set[str]], list[str]]`
- Produces: `build_backtest_payload(args, tq) -> dict[str, Any]`
- Produces: `render_backtest_markdown(payload) -> str`
- Produces: `write_backtest_outputs(payload, output_dir) -> tuple[Path, Path]`

- [ ] **Step 1: Add failing orchestration tests with a fake TQ client**

The fake client records `start_time`, `end_time`, `count`, `return_date` and returns two concept boards, dated XG data and OHLCVA field frames. Assert:

```python
self.assertEqual(formula_call["count"], 0)
self.assertTrue(formula_call["return_date"])
self.assertEqual(payload["top_limit"], 5)
self.assertTrue(all(1 <= row["rank"] <= 5 for row in payload["picks"]))
self.assertIn("当前板块成分", render_backtest_markdown(payload))
```

- [ ] **Step 2: Run orchestration tests and verify failure**

Run: `python3 -m unittest tools/dashboard/test_tdx_brick_backtest.py -v`

Expected: FAIL on missing orchestration functions.

- [ ] **Step 3: Implement historical fetch and daily replay**

`fetch_historical_bars` calls `get_market_data` with `period="1d"`, `count=0`, `start_time=YYYYMMDD`, `end_time=YYYYMMDD`, `dividend_type="none"`, then reuses brick field normalization. `build_backtest_payload`:

1. validates dates and derives warmup start `start-400 days` and query end `end+14 days`;
2. ranks five concept boards for every requested trading date;
3. fetches current members only for boards that entered a daily Top 5;
4. fetches stock bars and both historical signal maps in batches;
5. slices each candidate's bars through the signal date and calls shared `rank_sector_candidates` plus `apply_sector_rank_score_caps`;
6. constructs a historical snapshot from signal close and an estimated code-based upper limit;
7. globally deduplicates, keeps positive Top 5 and attaches future outcomes;
8. summarizes Top 1/3/5, signal sources and score buckets.

- [ ] **Step 4: Implement Markdown and CSV output**

Default output path:

```python
ROOT / "data" / "tdx-brick-selector" / "backtests" / f"{start:%Y%m%d}_{end:%Y%m%d}"
```

Write `report.md` with Top 3 first, then Top 1/5, source groups, score buckets, daily picks, errors and limitations. Write `picks.csv` with stable columns and empty strings for missing outcomes; reject non-finite floats before serialization.

- [ ] **Step 5: Run backtest tests**

Run: `python3 -m unittest tools/dashboard/test_tdx_brick_backtest.py -v`

Expected: all backtest tests PASS.

- [ ] **Step 6: Commit orchestration and reports**

```bash
git add modules/short_term/backtest_brick.py tools/dashboard/test_tdx_brick_backtest.py
git commit -m "feat: add tdx brick scoring backtest"
```

### Task 4: Add launcher, documentation and full regression

**Files:**
- Modify: `invest.py`
- Modify: `tools/dashboard/test_invest_launcher.py`
- Modify: `modules/short_term/README.md`
- Modify: `knowledge/wiki/portfolios/short-term-momentum-brick-indicator-system.md`
- Modify: `knowledge/wiki/log.md`

**Interfaces:**
- Produces: `python invest.py short backtest-brick --start-date ... --end-date ...`
- Preserves: existing `python invest.py short brick ...` route.

- [ ] **Step 1: Write failing launcher test**

```python
backtest = invest.build_command(["short", "backtest-brick", "--start-date", "2026-01-01", "--end-date", "2026-06-30"])
self.assertEqual(backtest[:2], [sys.executable, str(ROOT / "modules" / "short_term" / "backtest_brick.py")])
```

- [ ] **Step 2: Run launcher test and verify failure**

Run: `python3 -m unittest tools/dashboard/test_invest_launcher.py -v`

Expected: FAIL because the route still delegates to `run.py`.

- [ ] **Step 3: Add route and user-facing documentation**

Define `BACKTEST_BRICK_CLI` beside `SHORT_CLI`; dispatch `short backtest-brick` before the generic short route. Document both direct and launcher commands, the Top 5/Top 3 scope, current-member bias, and the fact that the report is a scoring validation rather than a portfolio backtest. Update the strategy wiki with the confirmed continuous weights and backtest limitations.

- [ ] **Step 4: Run full verification**

Run: `python3 -m unittest discover -s tools/dashboard -p 'test_*.py'`

Run: `node tools/dashboard/test_dashboard_core.mjs`

Run: `env PYTHONPYCACHEPREFIX=/tmp/wiki-pycache python3 -m py_compile modules/short_term/strategies/brick.py modules/short_term/backtest_brick.py invest.py`

Run: `git diff --check`

Expected: every command exits 0; no test failure, syntax error or whitespace error.

- [ ] **Step 5: Commit launcher and docs**

```bash
git add invest.py tools/dashboard/test_invest_launcher.py modules/short_term/README.md knowledge/wiki/portfolios/short-term-momentum-brick-indicator-system.md knowledge/wiki/log.md
git commit -m "docs: expose brick backtest workflow"
```
