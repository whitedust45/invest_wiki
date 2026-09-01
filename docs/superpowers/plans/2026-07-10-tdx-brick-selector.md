# 通达信砖型图尾盘筛选器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 在 Windows 通达信 TQ 环境中，于 14:40 从涨幅前五概念板块的成分股内输出原生公式 砖 与 Python 严格砖型图信号的对照结果。

**Architecture:** 新脚本将指标数学、TQ 适配、筛选编排和 CLI 输出分离为纯函数与边界函数。纯函数不导入 tqcenter，可在 macOS 单测；边界函数仅在运行时将 F:\new_tdx64\PYPlugins 加入 sys.path 并连接已登录的 Windows 通达信客户端。

**Tech Stack:** Python 3 标准库、通达信 TQ tqcenter.py、现有 unittest。

## Global Constraints

- 仅支持 Windows + 已启动并登录的通达信客户端；缺失时不得降级到 Tushare 或免费行情源。
- 默认通达信目录为 F:\new_tdx64，允许 TDX_PATH 和 --tdx-path 覆盖。
- 只扫描概念板块涨幅前 --concept-limit 5 的成分股，绝不扫描全 A 股。
- 原生引擎仅原样调用用户已保存的选股公式 砖；不得尝试创建或修改该公式。
- Python 引擎最终条件必须是今日严格红柱、昨日严格绿柱、今日红柱长度大于昨日绿柱长度的 2/3。
- 脚本不下单、不写 SQLite、不创建定时任务；默认仅输出终端结果。
- 仅进行本地 Git add/commit，禁止 push。

---

### Task 1: 建立可测试的砖型图数学与候选模型

**Files:**
- Create: tools/dashboard/tdx_brick_selector.py
- Create: tools/dashboard/test_tdx_brick_selector.py

**Interfaces:**
- Produces: DailyBar, BrickSignal, sma, calculate_brick_series, strict_brick_signal, rank_by_change_pct, merge_sector_members, extract_symbols。
- Consumes: 仅标准库数据类型；不得在模块加载时导入 tqcenter。

- [x] **Step 1: Write the failing test**

    from tdx_brick_selector import DailyBar, calculate_brick_series, merge_sector_members, rank_by_change_pct, sma, strict_brick_signal

    def test_sma_matches_tdx_m_equals_one_recurrence():
        assert sma([1.0, 2.0, 3.0], period=4) == [1.0, 1.25, 1.6875]

    def test_strict_signal_requires_yesterday_green_and_two_thirds_strength():
        signal = strict_brick_signal([2.0, 1.0, 2.0])
        assert signal is not None
        assert signal.today_red_length == 1.0
        assert signal.yesterday_green_length == 1.0

    def test_strict_signal_rejects_equal_yesterday_bar():
        assert strict_brick_signal([1.0, 1.0, 2.0]) is None

    def test_zero_four_bar_range_has_no_brick_signal():
        bars = [DailyBar('2026-07-01', 10.0, 10.0, 10.0)] * 4
        assert calculate_brick_series(bars) == []

    def test_rank_and_member_merge_preserve_sector_provenance():
        ranked = rank_by_change_pct({'880501.SH': [10.0, 11.0], '880502.SH': [10.0, 10.5]}, 1)
        assert ranked == [('880501.SH', 10.0)]
        assert merge_sector_members({'880501.SH': ['000001.SZ', '600000.SH'], '880502.SH': ['000001.SZ']}) == {
            '000001.SZ': ['880501.SH', '880502.SH'],
            '600000.SH': ['880501.SH'],
        }

- [x] **Step 2: Run test to verify it fails**

Run: python3 -m unittest tools/dashboard/test_tdx_brick_selector.py -v

Expected: FAIL with ModuleNotFoundError because tdx_brick_selector.py does not exist.

- [x] **Step 3: Write minimal implementation**

    @dataclass(frozen=True)
    class DailyBar:
        date: str
        high: float
        low: float
        close: float

    @dataclass(frozen=True)
    class BrickSignal:
        brick_t_minus_2: float
        brick_t_minus_1: float
        brick_t: float
        today_red_length: float
        yesterday_green_length: float

    def sma(values: list[float], period: int) -> list[float]:
        result: list[float] = []
        for value in values:
            result.append(value if not result else (value + (period - 1) * result[-1]) / period)
        return result

    def strict_brick_signal(bricks: list[float]) -> BrickSignal | None:
        if len(bricks) < 3:
            return None
        previous_previous, previous, current = bricks[-3:]
        red_length = current - previous
        green_length = previous_previous - previous
        if current <= previous or previous >= previous_previous or red_length <= green_length * 2 / 3:
            return None
        return BrickSignal(previous_previous, previous, current, red_length, green_length)

Implement calculate_brick_series with the confirmed VAR1A through VAR6A chain; return [] for a zero HHV(4)-LLV(4) range. Implement rank_by_change_pct as (latest / previous - 1) * 100, descending by percentage then symbol, and merge members into sorted, de-duplicated sector lists.

- [x] **Step 4: Run test to verify it passes**

Run: python3 -m unittest tools/dashboard/test_tdx_brick_selector.py -v

Expected: PASS for all five tests.

- [x] **Step 5: Commit**

Run: git add tools/dashboard/tdx_brick_selector.py tools/dashboard/test_tdx_brick_selector.py
Run: git commit -m "feat: add brick signal calculation"

### Task 2: 实现 Windows TQ 数据适配和双引擎筛选

**Files:**
- Modify: tools/dashboard/tdx_brick_selector.py
- Modify: tools/dashboard/test_tdx_brick_selector.py

**Interfaces:**
- Consumes: Task 1 的 DailyBar, BrickSignal, calculate_brick_series, strict_brick_signal, rank_by_change_pct, merge_sector_members, extract_symbols。
- Produces: load_tq, fetch_concept_boards, fetch_board_bars, select_top_concepts, fetch_sector_members, run_native_selection, run_python_selection, SelectionResult。

- [x] **Step 1: Write the failing test**

    class FakeTq:
        def get_sector_list(self, list_type):
            assert list_type == 1
            return ['880501.SH', '880502.SH', '880201.SH']

        def get_stock_list_in_sector(self, code):
            return {'880501.SH': ['000001.SZ'], '880502.SH': ['000001.SZ', '600000.SH']}[code]

    def test_native_result_symbols_are_recursively_extracted():
        raw = {'Value': {'matches': ['000001.SZ'], 'other': {'Code': '600000.SH'}}}
        assert extract_symbols(raw) == {'000001.SZ', '600000.SH'}

    def test_concept_members_are_fetched_without_a_share_scan():
        assert fetch_concept_boards(FakeTq()) == ['880501.SH', '880502.SH']
        assert fetch_sector_members(FakeTq(), ['880501.SH', '880502.SH']) == {
            '000001.SZ': ['880501.SH', '880502.SH'],
            '600000.SH': ['880502.SH'],
        }

- [x] **Step 2: Run test to verify it fails**

Run: python3 -m unittest tools/dashboard/test_tdx_brick_selector.py -v

Expected: FAIL with missing fetch_concept_boards or fetch_sector_members.

- [x] **Step 3: Write minimal implementation**

    def load_tq(tdx_path: str):
        root = Path(tdx_path).expanduser().resolve()
        plugin_dir = root / 'PYPlugins'
        system_dir = plugin_dir / 'sys'
        if not (system_dir / 'tqcenter.py').is_file():
            raise RuntimeError(f'未找到通达信 TQ 模块: {system_dir / "tqcenter.py"}')
        for directory in (str(plugin_dir), str(system_dir)):
            if directory not in sys.path:
                sys.path.insert(0, directory)
        from tqcenter import tq
        tq.initialize(__file__)
        return tq

    def fetch_concept_boards(tq) -> list[str]:
        return sorted({str(code).strip() for code in tq.get_sector_list(list_type=1) if str(code).strip().startswith('8805')})

    def run_native_selection(tq, symbols: list[str], formula_name: str, batch_size: int) -> tuple[set[str], list[str]]:
        matches: set[str] = set()
        errors: list[str] = []
        for batch in chunks(symbols, batch_size):
            raw = tq.formula_process_mul_xg(formula_name=formula_name, stock_list=batch, stock_period='1d', return_count=1)
            matches.update(extract_symbols(raw))
        return matches, errors

Before querying boards call tq.refresh_cache(market='AG'). Fetch board and component daily bars in batches using tq.get_market_data(period='1d', count=2 or bar_count, dividend_type='none'); normalize the field-keyed DataFrame dictionary to dict[str, list[DailyBar]]. Reject a run if the latest board or component bar date is not the local current trading date. Batch native calls at 200 symbols and continue on a single batch error while retaining error text.

- [x] **Step 4: Run test to verify it passes**

Run: python3 -m unittest tools/dashboard/test_tdx_brick_selector.py -v

Expected: PASS; no test imports tqcenter or requires Windows.

- [x] **Step 5: Commit**

Run: git add tools/dashboard/tdx_brick_selector.py tools/dashboard/test_tdx_brick_selector.py
Run: git commit -m "feat: add tdx brick selector engines"

### Task 3: 完成 CLI、报告与知识库同步

**Files:**
- Modify: tools/dashboard/tdx_brick_selector.py
- Modify: tools/dashboard/test_tdx_brick_selector.py
- Modify: knowledge/wiki/portfolios/short-term-momentum-brick-indicator-system.md
- Modify: knowledge/wiki/log.md
- Modify: docs/designs/tdx-brick-selector.md

**Interfaces:**
- Consumes: Task 2 的 load_tq, select_top_concepts, run_native_selection, run_python_selection, SelectionResult。
- Produces: parse_args, main, JSON payload and human-readable report.

- [x] **Step 1: Write the failing test**

    def test_cli_defaults_match_the_approved_execution_scope():
        args = parse_args([])
        assert args.engine == 'both'
        assert args.concept_limit == 5
        assert args.formula_name == '砖'
        assert args.tdx_path == r'F:\new_tdx64'
        assert args.batch_size == 200

    def test_comparison_reports_shared_and_engine_only_symbols():
        assert compare_matches({'000001.SZ', '600000.SH'}, {'000001.SZ', '300001.SZ'}) == {
            'shared': ['000001.SZ'],
            'native_only': ['600000.SH'],
            'python_only': ['300001.SZ'],
        }

- [x] **Step 2: Run test to verify it fails**

Run: python3 -m unittest tools/dashboard/test_tdx_brick_selector.py -v

Expected: FAIL with missing parse_args or compare_matches.

- [x] **Step 3: Write minimal implementation**

    def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
        parser = argparse.ArgumentParser(description='通达信砖型图尾盘概念板块筛选器')
        parser.add_argument('--tdx-path', default=os.environ.get('TDX_PATH', r'F:\new_tdx64'))
        parser.add_argument('--formula-name', default='砖')
        parser.add_argument('--engine', choices=('native', 'python', 'both'), default='both')
        parser.add_argument('--concept-limit', type=int, default=5)
        parser.add_argument('--bar-count', type=int, default=60)
        parser.add_argument('--batch-size', type=int, default=200)
        parser.add_argument('--json', action='store_true')
        parser.add_argument('--output')
        return parser.parse_args(argv)

    def compare_matches(native: set[str], python: set[str]) -> dict[str, list[str]]:
        return {
            'shared': sorted(native & python),
            'native_only': sorted(native - python),
            'python_only': sorted(python - native),
        }

main must call tq.close() when available, and emit JSON or a table containing runtime, concepts, members, matches, Python values, comparison, errors, and stage timings. --output only writes the user-specified path. Update the strategy page version to 2026-07-10 and add sections for original formula, strict Python rule, 14:40 top-five concept execution, and native-versus-Python differences. Add a think log entry with the user-provided formula as its source.

- [x] **Step 4: Run all local verification**

Run: python3 -m unittest discover -s tools/dashboard -p 'test_*.py'
Expected: PASS, including test_tdx_brick_selector.py without Windows.

Run: python3 -m py_compile tools/dashboard/tdx_brick_selector.py
Expected: exit code 0.

- [ ] **Step 5: Execute Windows validation**

Run on Windows at 14:40: python tools/dashboard/tdx_brick_selector.py --tdx-path F:\new_tdx64 --engine both --json

Expected: Output top-five concepts, de-duplicated member count, native formula 砖 matches, Python strict matches, comparison, and data time. If TQ does not return a current-day K line, output an error and no candidate.

- [x] **Step 6: Commit**

Run: git add tools/dashboard/tdx_brick_selector.py tools/dashboard/test_tdx_brick_selector.py knowledge/wiki/portfolios/short-term-momentum-brick-indicator-system.md knowledge/wiki/log.md docs/designs/tdx-brick-selector.md
Run: git commit -m "feat: add tdx brick selector cli"

## Self-Review

- Spec coverage: Task 1 covers formula and strict rules; Task 2 covers TQ path, top-five concepts, members, and native formula; Task 3 covers CLI, comparison, wiki, tests, and the Windows command.
- Placeholder scan: No unresolved markers or undefined implementation boundaries remain.
- Type consistency: DailyBar is generated by the TQ normalization layer and consumed by the formula layer; both engines return set[str] and compare_matches compares them; the CLI consumes only the orchestration functions listed above.
