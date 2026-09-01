# 通达信砖型图定时运行器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Windows 通达信砖型图筛选器提供工作日 14:40 自动启动、TQ 就绪等待、结果落盘和一次性任务计划安装能力。

**Architecture:** Python 筛选器增加只读 `--preflight` 入口，用于判断 TQ 与当前概念板块日线是否可用。PowerShell 运行器管理 `TdxW.exe` 启动、14:55 截止和按日落盘；安装器注册当前登录用户的 Windows 任务计划。

**Tech Stack:** Python 3 标准库与 unittest、Windows PowerShell、Windows ScheduledTasks 模块。

## Global Constraints

- 仅支持 Windows 当前登录用户会话，任务触发日为周一至周五 14:40。
- 默认通达信程序为 `F:\new_tdx64\TdxW.exe`，未运行时自动启动；不自动登录、不关闭客户端。
- 每 30 秒预检一次，最晚 14:55；超时失败且不补跑。
- 默认筛选维持 `--engine both --concept-limit 5 --formula-name 砖`。
- 结果写入 `data/tdx-brick-selector/runs/YYYY-MM-DD.json`，日志写入 `data/tdx-brick-selector/logs/YYYY-MM-DD.log`。
- 不接入交易日历、不下单、不写 SQLite、不推送远程。

---

### Task 1: 增加 Python TQ 预检接口

**Files:**
- Modify: `tools/dashboard/tdx_brick_selector.py`
- Modify: `tools/dashboard/test_tdx_brick_selector.py`

**Interfaces:**
- Produces: `build_preflight_payload(args, tq) -> dict[str, Any]` 和 `--preflight`。
- Consumes: `diagnose_concepts(tq, expected_date, batch_size)`。

- [x] **Step 1: Write the failing test**

```python
def test_preflight_is_ready_only_with_enough_current_concept_bars(self) -> None:
    args = selector.parse_args(["--concept-limit", "2"])
    payload = selector.build_preflight_payload(args, FakeTq())
    self.assertTrue(payload["ready"])
    self.assertEqual(payload["reason"], "ready")
```

- [x] **Step 2: Run test to verify it fails**

Run: `python3 -m unittest tools/dashboard/test_tdx_brick_selector.py -v`

Expected: import or attribute error for `build_preflight_payload`.

- [x] **Step 3: Write minimal implementation**

```python
def build_preflight_payload(args, tq):
    diagnostics = diagnose_concepts(tq, _current_date(), args.batch_size)
    ready = (
        not diagnostics["errors"]
        and diagnostics["current_day_concept_count"] >= args.concept_limit
    )
    return {"ready": ready, "reason": "ready" if ready else "concept_data_unavailable", **diagnostics}
```

Add `--preflight`. In `main`, write its JSON/table through `--output` and return `0` only when `ready` is true; do not invoke the native formula or component-stock scan.

- [x] **Step 4: Run test to verify it passes**

Run: `python3 -m unittest tools/dashboard/test_tdx_brick_selector.py -v`

Expected: PASS.

- [x] **Step 5: Commit**

Run: `git add tools/dashboard/tdx_brick_selector.py tools/dashboard/test_tdx_brick_selector.py`

Run: `git commit -m "feat: add tdx preflight mode"`

### Task 2: 新增 PowerShell 运行器与静态测试

**Files:**
- Create: `tools/dashboard/run_tdx_brick_selector.ps1`
- Create: `tools/dashboard/test_tdx_scheduled_runner_assets.py`

**Interfaces:**
- Consumes: Python `--preflight --json --output` and normal selector CLI.
- Produces: a date-scoped JSON run result and text log; process exit code `0` on a completed selector run, nonzero on deadline or selector failure.

- [x] **Step 1: Write the failing test**

```python
def test_runner_has_start_wait_deadline_and_daily_output_contract(self) -> None:
    source = RUNNER.read_text(encoding="utf-8")
    for token in (
        "TdxW.exe", "Start-Process", "Start-Sleep", "14:55",
        "--preflight", "--engine", "both", "tdx-brick-selector",
    ):
        self.assertIn(token, source)
```

- [x] **Step 2: Run test to verify it fails**

Run: `python3 -m unittest tools/dashboard/test_tdx_scheduled_runner_assets.py -v`

Expected: FAIL because the runner file does not exist.

- [x] **Step 3: Write minimal implementation**

The runner must:

```powershell
if (-not (Get-Process -Name "TdxW" -ErrorAction SilentlyContinue)) {
  Start-Process -FilePath $TdxExe -WorkingDirectory (Split-Path $TdxExe)
}
while ((Get-Date) -le $deadline) {
  & $PythonExe @pythonVersionArgs $Selector --preflight --json --output $PreflightPath
  if ($LASTEXITCODE -eq 0) { break }
  Start-Sleep -Seconds $PollSeconds
}
```

Create result/log directories under `$ProjectRoot/data/tdx-brick-selector`. Use `Tee-Object -Append` for runner and Python output. If the deadline is exceeded, log and `exit 1`. On preflight success, call the selector with `--engine both --json --output $ResultPath`; preserve that process exit code.

- [x] **Step 4: Run test to verify it passes**

Run: `python3 -m unittest tools/dashboard/test_tdx_scheduled_runner_assets.py -v`

Expected: PASS.

- [x] **Step 5: Commit**

Run: `git add tools/dashboard/run_tdx_brick_selector.ps1 tools/dashboard/test_tdx_scheduled_runner_assets.py`

Run: `git commit -m "feat: add tdx scheduled runner"`

### Task 3: 新增 Windows 任务计划安装器和使用说明

**Files:**
- Create: `tools/dashboard/install_tdx_brick_selector_task.ps1`
- Modify: `docs/designs/tdx-brick-selector.md`
- Modify: `knowledge/wiki/log.md`
- Modify: `tools/dashboard/test_tdx_scheduled_runner_assets.py`

**Interfaces:**
- Consumes: `run_tdx_brick_selector.ps1`。
- Produces: current-user task named `TDX Brick Selector 14:40`.

- [x] **Step 1: Write the failing test**

```python
def test_installer_registers_a_weekday_interactive_task_without_catch_up(self) -> None:
    source = INSTALLER.read_text(encoding="utf-8")
    for token in (
        "Register-ScheduledTask", "TDX Brick Selector 14:40",
        "Monday", "Friday", "14:40", "Interactive", "IgnoreNew",
    ):
        self.assertIn(token, source)
```

- [x] **Step 2: Run test to verify it fails**

Run: `python3 -m unittest tools/dashboard/test_tdx_scheduled_runner_assets.py -v`

Expected: FAIL because the installer file does not exist.

- [x] **Step 3: Write minimal implementation**

The installer derives `$ProjectRoot` from `$PSScriptRoot`, checks `py` then `python`, creates a weekly trigger for Monday through Friday at 14:40, and registers an interactive current-user task. Configure `-MultipleInstances IgnoreNew` and do not enable start-when-available catch-up. Use `Register-ScheduledTask -Force` to update an existing task.

Document the one-time Windows command:

```powershell
powershell -ExecutionPolicy Bypass -File tools\dashboard\install_tdx_brick_selector_task.ps1
```

Update the design and knowledge log with the new scheduled-run behavior and its Windows-only requirements.

- [x] **Step 4: Run tests and static checks**

Run: `python3 -m unittest discover -s tools/dashboard -p 'test_*.py'`

Run: `PYTHONPYCACHEPREFIX=/private/tmp/tdx-scheduled-pycache python3 -m py_compile tools/dashboard/tdx_brick_selector.py tools/dashboard/test_tdx_brick_selector.py`

Expected: PASS.

- [x] **Step 5: Commit**

Run: `git add tools/dashboard/install_tdx_brick_selector_task.ps1 tools/dashboard/test_tdx_scheduled_runner_assets.py docs/designs/tdx-brick-selector.md knowledge/wiki/log.md`

Run: `git commit -m "feat: install tdx weekday task"`

### Task 4: 新增 Windows 主动核对脚本

**Files:**
- Create: `tools/dashboard/verify_tdx_brick_selector_manual.py`
- Create: `tools/dashboard/test_tdx_manual_verifier.py`

**Interfaces:**
- Consumes: `tdx_brick_selector.py --engine both --json --output` 的 JSON 和用户从通达信导出的代码文本。
- Produces: `data/tdx-brick-selector/manual/YYYY-MM-DD.json` 和 `YYYY-MM-DD.compare.json`。

- [x] **Step 1: Write the failing test**

```python
def test_manual_codes_resolve_against_selected_sector_members() -> None:
    symbols, unresolved = resolve_manual_symbols(
        "000001\n600000.SH\n999999",
        {"000001.SZ": [], "600000.SH": []},
    )
    assert symbols == {"000001.SZ", "600000.SH"}
    assert unresolved == ["999999"]
```

- [x] **Step 2: Run test to verify it fails**

Run: `python3 -m unittest tools/dashboard/test_tdx_manual_verifier.py -v`

Expected: FAIL because the verifier module does not exist.

- [x] **Step 3: Write minimal implementation**

The verifier calls the selector with `--engine both --json --output`, reads its result JSON, resolves manual code tokens against `sector_members`, and writes `manual`, `native`, `python`, `manual_vs_native`, `manual_vs_python`, and unresolved manual tokens to a comparison JSON. `--manual-file` and repeatable `--manual-code` are optional inputs.

- [x] **Step 4: Run test to verify it passes**

Run: `python3 -m unittest tools/dashboard/test_tdx_manual_verifier.py -v`

Expected: PASS.

- [x] **Step 5: Commit**

Run: `git add tools/dashboard/verify_tdx_brick_selector_manual.py tools/dashboard/test_tdx_manual_verifier.py`

Run: `git commit -m "feat: add tdx manual comparison verifier"`

## Self-Review

- Spec coverage: Task 1 provides safe readiness detection; Task 2 provides automatic client launch, retry, deadline, JSON and logging; Task 3 provides weekday current-user installation and operation documentation; Task 4 provides the requested active manual comparison path.
- Placeholder scan: no unresolved implementation choices remain; the Windows executable, schedule, deadline, polling, paths and non-goals are exact.
- Type consistency: Task 2 invokes only the Task 1 CLI contract; Task 3 invokes only Task 2's runner path.
