#!/usr/bin/env python3
"""One-command launcher for the investment workspace."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
LEDGER_CLI = ROOT / "modules" / "ledger" / "local_service.py"
QUOTES_CLI = ROOT / "modules" / "market_data" / "quotes.py"
VALUATION_CLI = ROOT / "modules" / "market_data" / "valuation.py"
SHORT_CLI = ROOT / "modules" / "short_term" / "run.py"
VERIFY_BRICK_CLI = ROOT / "modules" / "short_term" / "verify_brick.py"
BACKTEST_BRICK_CLI = ROOT / "modules" / "short_term" / "backtest_brick.py"
BACKTEST_TAIL_CLI = ROOT / "modules" / "short_term" / "backtest_tail.py"

HELP = """使用方式:
  python3 invest.py ledger [--port 8775]
  python3 invest.py market quotes <代码...> [--history-days 260]
  python3 invest.py market valuation [--no-include-basis]
  python3 invest.py short [策略ID] [策略参数...]
  python3 invest.py short verify-brick [核对参数...]
  python3 invest.py short backtest-brick --start-date YYYY-MM-DD --end-date YYYY-MM-DD
  python3 invest.py short backtest-tail --strategy steady_momentum --symbols-file 股票池.txt --start-date YYYY-MM-DD --end-date YYYY-MM-DD

常用:
  python3 invest.py ledger
  python3 invest.py market quotes 000858 600036 --history-days 260
  python3 invest.py market valuation
  python3 invest.py short
  python3 invest.py short brick --help
  python3 invest.py short backtest-brick --start-date 2026-01-01 --end-date 2026-06-30
  python3 invest.py short backtest-tail --strategy steady_momentum --symbols-file symbols.txt --start-date 2026-01-01 --end-date 2026-06-30
"""


def build_command(argv: list[str]) -> list[str]:
    if not argv:
        raise ValueError("缺少工作流")
    domain, *rest = argv
    if domain in {"ledger", "dashboard"}:
        return [sys.executable, str(LEDGER_CLI), *rest]
    if domain == "market":
        if not rest:
            raise ValueError("market 需要 quotes 或 valuation")
        workflow, *params = rest
        if workflow == "quotes":
            return [sys.executable, str(QUOTES_CLI), *params]
        if workflow == "valuation":
            return [sys.executable, str(VALUATION_CLI), *params]
        raise ValueError(f"未知市场数据工作流: {workflow}")
    if domain == "short":
        if rest and rest[0] == "verify-brick":
            return [sys.executable, str(VERIFY_BRICK_CLI), *rest[1:]]
        if rest and rest[0] == "backtest-brick":
            return [sys.executable, str(BACKTEST_BRICK_CLI), *rest[1:]]
        if rest and rest[0] == "backtest-tail":
            return [sys.executable, str(BACKTEST_TAIL_CLI), *rest[1:]]
        return [sys.executable, str(SHORT_CLI), *rest]
    raise ValueError(f"未知模块: {domain}")


def main(argv: list[str] | None = None) -> int:
    args = list(argv if argv is not None else sys.argv[1:])
    if args and args[0] in {"-h", "--help", "help"}:
        print(HELP)
        return 0
    dry_run = "--dry-run" in args
    args = [arg for arg in args if arg != "--dry-run"]
    try:
        command = build_command(args)
    except ValueError as error:
        print(f"错误: {error}\n\n{HELP}", file=sys.stderr)
        return 2
    if dry_run:
        print(" ".join(command))
        return 0
    return subprocess.run(command, cwd=ROOT, check=False).returncode


if __name__ == "__main__":
    raise SystemExit(main())
