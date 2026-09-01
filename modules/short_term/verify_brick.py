"""Actively run the TDX selector and compare it with manual TDX selections."""

from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Dict, Iterable, List, Optional, Set, Tuple


_SYMBOL_TOKEN = re.compile(r"\d{6}(?:\.(?:SH|SZ|BJ))?", re.IGNORECASE)
_QUALIFIED_SYMBOL = re.compile(r"\d{6}\.(?:SH|SZ|BJ)", re.IGNORECASE)


def resolve_manual_symbols(
    text: str, sector_members: Dict[str, Iterable[str]]
) -> Tuple[Set[str], List[str]]:
    """Resolve bare exported TDX codes against the selector's sector members."""
    candidates_by_code: Dict[str, Set[str]] = defaultdict(set)
    for symbol in sector_members:
        candidates_by_code[symbol[:6]].add(symbol.upper())

    resolved: Set[str] = set()
    unresolved: List[str] = []
    for raw_token in _SYMBOL_TOKEN.findall(text):
        token = raw_token.upper()
        if _QUALIFIED_SYMBOL.fullmatch(token):
            resolved.add(token)
            continue
        candidates = candidates_by_code.get(token, set())
        if len(candidates) == 1:
            resolved.update(candidates)
        else:
            unresolved.append(token)
    return resolved, sorted(set(unresolved))


def compare_symbol_sets(manual: Set[str], engine: Set[str]) -> Dict[str, List[str]]:
    return {
        "shared": sorted(manual & engine),
        "manual_only": sorted(manual - engine),
        "engine_only": sorted(engine - manual),
    }


def render_comparison_markdown(comparison: Dict[str, object]) -> str:
    """Render the optional manual-vs-engine diagnostic without exposing JSON files."""
    lines = [
        "# 通达信手工选股核对",
        "",
        f"- 手工导入股票数：{len(comparison['manual'])}",
        f"- 未解析代码：{', '.join(comparison['unresolved_manual_tokens']) or '-'}",
        "",
        "| 引擎 | 与手工一致 | 仅手工 | 仅引擎 |",
        "| --- | --- | --- | --- |",
    ]
    for label, key in (("原生 ZHUAN", "manual_vs_native"), ("Python 砖型图", "manual_vs_python")):
        result = comparison[key]
        lines.append(
            f"| {label} | {', '.join(result['shared']) or '-'} | "
            f"{', '.join(result['manual_only']) or '-'} | {', '.join(result['engine_only']) or '-'} |"
        )
    return "\n".join(lines) + "\n"


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description="通达信砖型图主动运行与手工结果核对")
    parser.add_argument("--tdx-path", default=os.environ.get("TDX_PATH", r"F:\new_tdx64"))
    parser.add_argument("--manual-file", type=Path, help="通达信手工选股导出的代码文本")
    parser.add_argument("--manual-code", action="append", default=[], help="手工选股代码，可重复传入")
    parser.add_argument("--run-time", default="14:40")
    parser.add_argument("--selector", type=Path, default=script_dir / "strategies" / "brick.py")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=script_dir.parents[2] / "data" / "tdx-brick-selector" / "manual",
    )
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    if not args.selector.is_file():
        print(f"未找到筛选脚本: {args.selector}", file=sys.stderr)
        return 2

    date_key = datetime.now().strftime("%Y-%m-%d")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    result_path = args.output_dir / f".{date_key}.selector.json"
    comparison_path = args.output_dir / f"{date_key}.compare.md"
    command = [
        sys.executable,
        str(args.selector),
        "--tdx-path",
        args.tdx_path,
        "--engine",
        "both",
        "--run-time",
        args.run_time,
        "--json",
        "--output",
        str(result_path),
    ]
    completed = subprocess.run(command, text=True, capture_output=True, check=False)
    if completed.stderr:
        print(completed.stderr, end="", file=sys.stderr)
    if completed.returncode != 0:
        if completed.stdout:
            print(completed.stdout, end="")
        return completed.returncode

    payload = json.loads(result_path.read_text(encoding="utf-8"))
    manual_text = "\n".join(args.manual_code)
    if args.manual_file:
        manual_text = "\n".join(
            [manual_text, args.manual_file.read_text(encoding="utf-8")]
        )
    manual_symbols, unresolved = resolve_manual_symbols(
        manual_text, payload["sector_members"]
    )
    native_matches = set(payload["native"]["matches"])
    python_matches = set(payload["python"]["matches"])
    comparison: Dict[str, object] = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "manual": sorted(manual_symbols),
        "unresolved_manual_tokens": unresolved,
        "native": sorted(native_matches),
        "python": sorted(python_matches),
        "manual_vs_native": compare_symbol_sets(manual_symbols, native_matches),
        "manual_vs_python": compare_symbol_sets(manual_symbols, python_matches),
    }
    comparison_path.write_text(render_comparison_markdown(comparison), encoding="utf-8")
    result_path.unlink(missing_ok=True)
    print(comparison_path.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
