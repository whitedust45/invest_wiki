#!/usr/bin/env python3
"""Single CLI entrypoint for registered short-term strategies."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.short_term.registry import available_strategies, get_strategy


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="运行已注册的超短策略")
    parser.add_argument("strategy", nargs="?", help="策略 ID；省略时列出可用策略")
    parser.add_argument("strategy_args", nargs=argparse.REMAINDER, help="传递给策略的参数")
    args = parser.parse_args(argv)
    if not args.strategy:
        for strategy in available_strategies():
            print(f"{strategy.strategy_id}\t{strategy.display_name}\t{strategy.description}")
        return 0
    return get_strategy(args.strategy).run(args.strategy_args)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
