"""Extract auto-sync market instruments from investment wiki pages."""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
GOOD_COMPANIES = Path("knowledge/wiki/portfolios/a-share-good-companies-list.md")
HIGH_DIVIDEND = Path("knowledge/wiki/portfolios/high-dividend-cashflow-watchlist.md")
ENTITIES_DIR = Path("knowledge/wiki/entities")


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        return ""


def frontmatter(text: str) -> dict[str, str]:
    if not text.startswith("---"):
        return {}
    end = text.find("\n---", 3)
    if end < 0:
        return {}
    result: dict[str, str] = {}
    for raw in text[3:end].splitlines():
        if ":" not in raw:
            continue
        key, value = raw.split(":", 1)
        result[key.strip()] = value.strip()
    return result


def title_cn(title: str, fallback: str) -> str:
    if not title:
        return fallback
    return title.split("/")[0].strip() or fallback


def a_share_code_from_text(text: str) -> tuple[str, str]:
    match = re.search(r"\b(\d{6})\.(SH|SZ)\b", text)
    if match:
        return match.group(1), f"{match.group(1)}.{match.group(2)}"
    match = re.search(r"(?<![:\d])(\d{6})(?!\d)", text)
    if match:
        code = match.group(1)
        suffix = "SZ" if re.match(r"^(000|001|002|003|159|300|301)", code) else "SH"
        return code, f"{code}.{suffix}"
    return "", ""


def instrument_from_entity(root: Path, wiki_id: str, scope: str) -> dict[str, Any] | None:
    text = read_text(root / ENTITIES_DIR / f"{wiki_id}.md")
    if not text:
        return None
    meta = frontmatter(text)
    symbol, ts_code = a_share_code_from_text(meta.get("tags", "") + "\n" + text[:2000])
    if not symbol:
        return None
    return {
        "symbol": symbol,
        "ts_code": ts_code,
        "name": title_cn(meta.get("title", ""), symbol),
        "track_scope": scope,
        "source_wiki_id": wiki_id,
        "source_wiki_path": str(ENTITIES_DIR / f"{wiki_id}.md"),
    }


def extract_good_company_instruments(root: Path) -> list[dict[str, Any]]:
    text = read_text(root / GOOD_COMPANIES)
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for wiki_id in re.findall(r"\[\[([a-z0-9-]+)(?:\|[^\]]+)?\]\]", text):
        item = instrument_from_entity(root, wiki_id, "wiki_good_company")
        if item and item["symbol"] not in seen:
            result.append(item)
            seen.add(item["symbol"])
    return result


def extract_table_rows(text: str) -> list[list[str]]:
    rows = []
    for raw in text.splitlines():
        line = raw.strip()
        if not line.startswith("|") or "---" in line:
            continue
        cells = [cell.strip() for cell in line.strip("|").split("|")]
        if len(cells) >= 3:
            rows.append(cells)
    return rows


def extract_high_dividend_instruments(root: Path) -> list[dict[str, Any]]:
    text = read_text(root / HIGH_DIVIDEND)
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for cells in extract_table_rows(text):
        if cells[0] == "分层" or cells[1] == "代码":
            continue
        code = cells[1]
        if not re.fullmatch(r"\d{6}", code):
            continue
        symbol, ts_code = a_share_code_from_text(code)
        if not symbol or symbol in seen:
            continue
        result.append(
            {
                "symbol": symbol,
                "ts_code": ts_code,
                "name": cells[2],
                "track_scope": "wiki_watchlist",
                "source_wiki_id": "high-dividend-cashflow-watchlist",
                "source_wiki_path": str(HIGH_DIVIDEND),
            }
        )
        seen.add(symbol)
    return result


def merge_instruments(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_symbol: dict[str, dict[str, Any]] = {}
    priority = {"wiki_good_company": 0, "wiki_watchlist": 1}
    for item in items:
        symbol = item["symbol"]
        current = by_symbol.get(symbol)
        if current is None or priority.get(item["track_scope"], 99) < priority.get(current["track_scope"], 99):
            by_symbol[symbol] = item
    return [by_symbol[symbol] for symbol in sorted(by_symbol)]


def load_wiki_tracked_instruments(root: Path | str = ROOT) -> list[dict[str, Any]]:
    root_path = Path(root)
    return merge_instruments(
        [
            *extract_good_company_instruments(root_path),
            *extract_high_dividend_instruments(root_path),
        ]
    )


def load_wiki_tracked_symbols(root: Path | str = ROOT) -> list[str]:
    return [item["symbol"] for item in load_wiki_tracked_instruments(root)]
