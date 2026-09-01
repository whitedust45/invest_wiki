"""Pure Python mathematics and candidate helpers for the TDX brick selector."""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import asdict, dataclass, replace
from datetime import datetime
import json
import math
import os
from pathlib import Path
import re
import sys
import time
from typing import Any, Dict, Iterable, List, Optional, Sequence, Set, Tuple


@dataclass(frozen=True)
class DailyBar:
    date: str
    high: float
    low: float
    close: float
    open: float = 0.0
    volume: float = 0.0
    amount: float = 0.0


@dataclass(frozen=True)
class BrickSignal:
    brick_t_minus_2: float
    brick_t_minus_1: float
    brick_t: float
    today_red_length: float
    yesterday_green_length: float


@dataclass(frozen=True)
class MarketSnapshot:
    last_price: float
    upper_limit: float
    stock_name: str = ""


@dataclass(frozen=True)
class CandidateScore:
    symbol: str
    stock_name: str
    signal_source: str
    final_score: float
    base_score: float
    risk_penalty: float
    factor_scores: Dict[str, float]
    hard_filter_reasons: Tuple[str, ...]
    risk_reasons: Tuple[str, ...]
    metrics: Dict[str, float]
    sector_rank: int = 0
    sector_score_cap: Optional[float] = None


@dataclass
class SelectionResult:
    matches: Set[str]
    signals: Dict[str, BrickSignal]
    errors: List[str]


FACTOR_WEIGHTS = {
    "board_leadership": 15.0,
    "relative_strength": 10.0,
    "liquidity": 6.0,
    "tail_structure": 0.0,
    "brick_strength": 30.0,
    "white_yellow_trend": 14.0,
    "previous_kdj_j": 20.0,
    "previous_kdj_doji": 5.0,
}
TOP_CANDIDATES_PER_SECTOR = 3
EXECUTION_TOP_LIMIT = 3
SECTOR_RANK_SCORE_CAPS = (45.0, 35.0, 25.0)
DISTRIBUTION_LOOKBACK = 5


def sma(values: Sequence[float], period: int) -> List[float]:
    """Return TDX SMA(X, N, 1): seed with X and recursively smooth thereafter."""
    if period <= 0:
        raise ValueError("period must be positive")

    result: List[float] = []
    for value in values:
        result.append(value if not result else (value + (period - 1) * result[-1]) / period)
    return result


def calculate_brick_series(bars: Sequence[DailyBar]) -> List[float]:
    """Calculate brick values from the fourth bar onward using the confirmed chain."""
    if len(bars) < 4:
        return []

    windows: List[Tuple[float, float]] = []
    for index in range(3, len(bars)):
        window = bars[index - 3 : index + 1]
        highest = max(bar.high for bar in window)
        lowest = min(bar.low for bar in window)
        if highest - lowest == 0:
            return []
        windows.append((highest, lowest))

    var1a: List[float] = []
    var3a: List[float] = []
    for bar, (highest, lowest) in zip(bars[3:], windows):
        span = highest - lowest
        var1a.append((highest - bar.close) / span * 100 - 90)
        var3a.append((bar.close - lowest) / span * 100)

    var2a = [value + 100 for value in sma(var1a, 4)]
    var4a = sma(var3a, 6)
    var5a = [value + 100 for value in sma(var4a, 6)]
    return [max(value - var2 - 4, 0) for value, var2 in zip(var5a, var2a)]


def latest_brick_state(bricks: Sequence[float]) -> Optional[BrickSignal]:
    """Return the latest brick movement even when it is not a strict entry signal."""
    if len(bricks) < 3:
        return None

    previous_previous, previous, current = bricks[-3:]
    if not all(math.isfinite(value) for value in (previous_previous, previous, current)):
        return None
    red_length = current - previous
    green_length = previous_previous - previous
    return BrickSignal(previous_previous, previous, current, red_length, green_length)


def strict_brick_signal(bricks: Sequence[float]) -> Optional[BrickSignal]:
    """Match only strict red-today, green-yesterday and strength conditions."""
    signal = latest_brick_state(bricks)
    if signal is None:
        return None
    red_length = signal.today_red_length
    green_length = signal.yesterday_green_length
    if red_length <= 0 or green_length <= 0 or red_length <= green_length * 3 / 4:
        return None
    return signal


def rank_by_change_pct(history: Dict[str, Sequence[float]], limit: int) -> List[Tuple[str, float]]:
    """Rank symbols by their latest percentage change, descending then by symbol."""
    if limit <= 0:
        return []

    ranked: List[Tuple[str, float]] = []
    for symbol, values in history.items():
        if len(values) < 2 or values[-2] == 0:
            continue
        change_pct = (values[-1] / values[-2] - 1) * 100
        ranked.append((symbol, change_pct))
    ranked.sort(key=lambda item: (-item[1], item[0]))
    return ranked[:limit]


def merge_sector_members(sector_members: Dict[str, Iterable[str]]) -> Dict[str, List[str]]:
    """Invert sector-to-member data while preserving sorted unique provenance."""
    merged: Dict[str, Set[str]] = {}
    for sector, members in sector_members.items():
        for member in members:
            merged.setdefault(member, set()).add(sector)
    return {member: sorted(sectors) for member, sectors in sorted(merged.items())}


_SYMBOL_PATTERN = re.compile(r"\d{6}\.(?:SH|SZ|BJ)", re.IGNORECASE)


def extract_symbols(raw: Any) -> Set[str]:
    """Recursively extract exchange-qualified six-digit symbols from standard data."""
    symbols: Set[str] = set()
    if isinstance(raw, str):
        match = _SYMBOL_PATTERN.fullmatch(raw.strip())
        if match:
            symbols.add(match.group(0).upper())
    elif isinstance(raw, dict):
        for key, value in raw.items():
            symbols.update(extract_symbols(key))
            symbols.update(extract_symbols(value))
    elif isinstance(raw, (list, tuple, set, frozenset)):
        for value in raw:
            symbols.update(extract_symbols(value))
    return symbols


def _contains_formula_signal(raw: Any) -> bool:
    if isinstance(raw, dict):
        return any(
            str(key).strip().lower() == "xg" or _contains_formula_signal(value)
            for key, value in raw.items()
        )
    if isinstance(raw, (list, tuple, set, frozenset)):
        return any(_contains_formula_signal(value) for value in raw)
    return False


def _formula_signal_is_true(raw: Any) -> bool:
    if isinstance(raw, dict):
        for key, value in raw.items():
            if str(key).strip().lower() == "xg":
                return _formula_signal_is_true(value)
        return False
    if isinstance(raw, (list, tuple)):
        return bool(raw) and _formula_signal_is_true(raw[-1])
    if isinstance(raw, str):
        token = raw.strip().lower()
        if token in {"", "0", "false", "no", "n"}:
            return False
        if token in {"1", "true", "yes", "y"}:
            return True
        try:
            return float(token) != 0
        except ValueError:
            return False
    if isinstance(raw, (int, float)):
        return raw != 0
    return False


def _formula_symbol_entries(raw: Any) -> List[Tuple[str, Any]]:
    if not isinstance(raw, dict):
        return []
    return [
        (key.strip().upper(), value)
        for key, value in raw.items()
        if isinstance(key, str) and _SYMBOL_PATTERN.fullmatch(key.strip())
    ]


def extract_formula_matches(raw: Any, candidates: Sequence[str]) -> Set[str]:
    """Extract native XG matches from TQ's per-symbol or direct-list responses."""
    candidate_set = {symbol.upper() for symbol in candidates}
    if isinstance(raw, dict):
        symbol_entries = _formula_symbol_entries(raw)
        if symbol_entries:
            if not any(_contains_formula_signal(value) for _, value in symbol_entries):
                return set()
            return {
                symbol
                for symbol, value in symbol_entries
                if symbol in candidate_set and _formula_signal_is_true(value)
            }
        return set()
    return extract_symbols(raw).intersection(candidate_set)


def load_tq(tdx_path: str) -> Any:
    """Load and initialize the TQ client only when invoked on Windows."""
    root = Path(tdx_path).expanduser().resolve()
    plugin_dir = root / "PYPlugins"
    system_dir = plugin_dir / "sys"
    module_path = system_dir / "tqcenter.py"
    if not module_path.is_file():
        raise RuntimeError(f"未找到通达信 TQ 模块: {module_path}")
    for directory in (str(plugin_dir), str(system_dir)):
        if directory not in sys.path:
            sys.path.insert(0, directory)
    from tqcenter import tq

    tq.initialize(__file__)
    return tq


def chunks(values: Sequence[str], size: int) -> Iterable[List[str]]:
    """Yield fixed-size lists, rejecting invalid batch sizes early."""
    if size <= 0:
        raise ValueError("batch size must be positive")
    for index in range(0, len(values), size):
        yield list(values[index : index + size])


def _normalize_sector_code(raw_sector: Any) -> str:
    if isinstance(raw_sector, dict):
        for key, value in raw_sector.items():
            if str(key).strip().lower() == "code":
                return str(value).strip().upper()
        return ""
    return str(raw_sector).strip().upper()


def _normalize_sector_name(raw_sector: Any) -> str:
    if not isinstance(raw_sector, dict):
        return ""
    for key, value in raw_sector.items():
        if _normalized_snapshot_key(key) in {"name", "名称", "板块名称"}:
            return str(value).strip()
    return ""


def fetch_sector_codes(tq: Any) -> List[str]:
    """Return normalized A-share sector codes from TQ string or dictionary results."""
    tq.refresh_cache(market="AG")
    return sorted(
        {
            code
            for code in (
                _normalize_sector_code(raw_code)
                for raw_code in tq.get_sector_list(list_type=1)
            )
            if code
        }
    )


def fetch_sector_names(tq: Any) -> Dict[str, str]:
    """Return known TDX concept and sector display names keyed by code."""
    tq.refresh_cache(market="AG")
    names: Dict[str, str] = {}
    for raw_sector in tq.get_sector_list(list_type=1):
        code = _normalize_sector_code(raw_sector)
        name = _normalize_sector_name(raw_sector)
        if code and name:
            names[code] = name
    return names


def fetch_concept_boards_from_file(tdx_path: str) -> List[str]:
    """Read concept board codes from infoharbor_block.dat (~270 boards)."""
    block_file = Path(tdx_path).expanduser().resolve() / "T0002" / "hq_cache" / "infoharbor_block.dat"
    if not block_file.is_file():
        return []
    try:
        text = block_file.read_bytes().decode("gbk", errors="ignore")
    except Exception:
        return []
    codes: Set[str] = set()
    for line in text.splitlines():
        if not line.startswith("#GN_"):
            continue
        parts = line.split(",")
        for part in parts:
            part = part.strip()
            if len(part) == 6 and part.isdigit() and part.startswith("880"):
                codes.add(f"{part}.SH")
    return sorted(codes)


def fetch_concept_boards(tq: Any, tdx_path: str) -> List[str]:
    """Return concept-board codes from infoharbor_block.dat first, fallback to TQ."""
    file_codes = fetch_concept_boards_from_file(tdx_path)
    if file_codes:
        return file_codes
    # Fallback to TQ sector list
    return [
        code
        for code in fetch_sector_codes(tq)
        if code.split(".", 1)[0].startswith("8805")
        or code.split(".", 1)[0].startswith("8806")
        or code.split(".", 1)[0].startswith("8807")
        or code.split(".", 1)[0].startswith("8808")
        or code.split(".", 1)[0].startswith("8809")
    ]


def _normalize_date(value: Any) -> str:
    if hasattr(value, "strftime"):
        return value.strftime("%Y-%m-%d")
    text = str(value).strip().replace("T", " ")
    try:
        return datetime.fromisoformat(text).strftime("%Y-%m-%d")
    except ValueError as error:
        raise ValueError(f"无法解析日期 {value!r}") from error


def _find_field_frames(raw: Any) -> Dict[str, Any]:
    if not isinstance(raw, dict):
        raise ValueError("通达信日线数据不是字段字典")
    frames = {str(name).strip().lower(): frame for name, frame in raw.items()}
    missing = sorted({"open", "high", "low", "close", "volume", "amount"} - set(frames))
    if missing:
        raise ValueError(f"通达信日线数据缺少字段: {', '.join(missing)}")
    return frames


def _column_positions(frame: Any) -> Dict[str, int]:
    return {str(symbol).strip().upper(): index for index, symbol in enumerate(frame.columns)}


def _normalize_symbol_bars(frames: Dict[str, Any], symbol: str) -> List[DailyBar]:
    field_names = ("open", "high", "low", "close", "volume", "amount")
    field_frames = {name: frames[name] for name in field_names}
    positions = [_column_positions(field_frames[name]).get(symbol.upper()) for name in field_names]
    if any(position is None for position in positions):
        raise ValueError("日线数据缺少证券列")
    dates = [
        [_normalize_date(value) for value in field_frames[name].index]
        for name in field_names
    ]
    if any(current_dates != dates[0] for current_dates in dates[1:]):
        raise ValueError("日线字段日期不一致")
    position_by_field = dict(zip(field_names, positions))
    bars: List[DailyBar] = []
    skipped_dates: List[str] = []
    for row, date in enumerate(dates[0]):
        values = {
            name: float(field_frames[name].iloc[row, position_by_field[name]])
            for name in field_names
        }
        invalid_fields = [name for name, value in values.items() if not math.isfinite(value)]
        if invalid_fields:
            # 批量请求时，TQ返回所有股票的并集日期，某些股票在早期可能没有数据（NaN）
            # 跳过这些无效行而不是报错，只保留有有效数据的日子
            skipped_dates.append(date)
            continue
        bars.append(DailyBar(date=date, **values))
    if skipped_dates:
        # 只记录一次警告，避免日志过多
        pass  # 静默跳过，不在此处记录以免干扰
    if not bars:
        raise ValueError("所有日线数据均为无效值（可能该证券尚未上市或数据未下载）")
    return bars


def fetch_board_bars(
    tq: Any, symbols: List[str], count: int, batch_size: int = 200
) -> Tuple[Dict[str, List[DailyBar]], List[str]]:
    """Fetch unadjusted daily bars and normalize TQ field DataFrames by symbol."""
    bars: Dict[str, List[DailyBar]] = {}
    errors: List[str] = []
    for batch in chunks(symbols, batch_size):
        try:
            frames = _find_field_frames(
                tq.get_market_data(stock_list=batch, period="1d", count=count, dividend_type="none")
            )
        except Exception as error:
            errors.append(f"{', '.join(batch)}: 获取日线失败: {error}")
            continue
        for symbol in batch:
            try:
                normalized = _normalize_symbol_bars(frames, symbol)
                if not normalized:
                    raise ValueError("日线数据为空")
                bars[symbol] = normalized
            except Exception as error:
                errors.append(f"{symbol}: 日线数据无效: {error}")
    return bars, errors


def resolve_latest_concept_date(
    board_bars: Dict[str, List[DailyBar]], minimum_concept_count: int = 1
) -> Optional[str]:
    """Return the newest TDX trading date with enough complete concept bars."""
    date_counts = Counter(
        bars[-1].date
        for bars in board_bars.values()
        if len(bars) >= 2
    )
    eligible_dates = [
        date for date, count in date_counts.items() if count >= minimum_concept_count
    ]
    return max(eligible_dates, default=None)


def diagnose_concepts(
    tq: Any,
    expected_date: str,
    batch_size: int,
    tdx_path: str,
    minimum_concept_count: int = 1,
) -> Dict[str, Any]:
    """Expose counts and latest dates for the concept-board data pipeline."""
    concept_codes = fetch_concept_boards(tq, tdx_path)
    sector_prefix_counts = Counter(
        code.split(".", 1)[0][:4].upper() or "<empty>" for code in concept_codes
    )
    board_bars, errors = fetch_board_bars(
        tq, concept_codes, count=2, batch_size=batch_size
    )
    latest_date_counts = Counter(
        bars[-1].date for bars in board_bars.values() if len(bars) >= 2
    )
    data_date = resolve_latest_concept_date(board_bars, minimum_concept_count)
    current_day_concept_count = latest_date_counts.get(data_date, 0)
    return {
        "expected_date": expected_date,
        "requested_date": expected_date,
        "data_date": data_date,
        "fallback_used": data_date is not None and data_date != expected_date,
        "all_sector_count": len(concept_codes),
        "concept_code_count": len(concept_codes),
        "daily_bar_count": len(board_bars),
        "current_day_concept_count": current_day_concept_count,
        "latest_date_counts": dict(sorted(latest_date_counts.items())),
        "sector_prefix_counts": dict(sorted(sector_prefix_counts.items())),
        "all_sector_samples": concept_codes[:20],
        "concept_code_samples": concept_codes[:10],
        "errors": errors,
    }


def select_top_concepts(
    board_bars: Dict[str, List[DailyBar]], concept_limit: int, expected_date: str
) -> List[Tuple[str, float]]:
    """Rank only current-session concept boards by their unrounded close change."""
    valid_closes = {
        symbol: [bars[-2].close, bars[-1].close]
        for symbol, bars in board_bars.items()
        if len(bars) >= 2 and bars[-1].date == expected_date
    }
    ranked = rank_by_change_pct(valid_closes, concept_limit)
    if len(ranked) < concept_limit:
        raise RuntimeError(f"有效概念板块不足 {concept_limit} 个")
    return ranked


def fetch_sector_members(tq: Any, sector_codes: List[str]) -> Dict[str, List[str]]:
    """Fetch only selected concept members and retain sector provenance."""
    return merge_sector_members(
        {sector: tq.get_stock_list_in_sector(sector) for sector in sector_codes}
    )


def run_native_selection(
    tq: Any, symbols: List[str], formula_name: str, batch_size: int, formula_arg: str = ""
) -> SelectionResult:
    """Call the existing native formula verbatim, retaining batch failures."""
    matches: Set[str] = set()
    errors: List[str] = []
    for batch in chunks(symbols, batch_size):
        try:
            kwargs = {
                "formula_name": formula_name,
                "stock_list": batch,
                "stock_period": "1d",
                "return_count": 1,
            }
            if formula_arg:
                kwargs["formula_arg"] = formula_arg
            raw = tq.formula_process_mul_xg(**kwargs)
            error_id = raw.get("ErrorId") if isinstance(raw, dict) else None
            if error_id not in (None, "0", 0):
                error_text = raw.get("Error", "未知错误")
                errors.append(f"{', '.join(batch)}: 原生公式返回错误 {error_id}: {error_text}")
                continue
            symbol_entries = _formula_symbol_entries(raw)
            if symbol_entries and not any(
                _contains_formula_signal(value) for _, value in symbol_entries
            ):
                errors.append(f"{', '.join(batch)}: 原生公式返回未包含 XG 字段，已忽略该批次")
                continue
            matches.update(extract_formula_matches(raw, batch))
        except Exception as error:
            errors.append(f"{', '.join(batch)}: 原生公式调用失败: {error}")
    return SelectionResult(matches=matches, signals={}, errors=errors)


def run_python_selection(
    stock_bars: Dict[str, List[DailyBar]], expected_date: str
) -> SelectionResult:
    """Apply the strict Python brick rule to current-session component bars."""
    signals: Dict[str, BrickSignal] = {}
    errors: List[str] = []
    for symbol, bars in stock_bars.items():
        if not bars:
            errors.append(f"{symbol}: 日线数据为空")
            continue
        if bars[-1].date != expected_date:
            errors.append(f"{symbol}: 最新日线日期 {bars[-1].date} 不等于 {expected_date}")
            continue
        try:
            bricks = calculate_brick_series(bars)
        except Exception as error:
            errors.append(f"{symbol}: 砖型图计算失败: {error}")
            continue
        if not bricks:
            errors.append(f"{symbol}: 无有效砖型图数据")
            continue
        signal = strict_brick_signal(bricks)
        if signal is not None:
            signals[symbol] = signal
    return SelectionResult(matches=set(signals), signals=signals, errors=errors)


def _normalized_snapshot_key(value: Any) -> str:
    return re.sub(r"[\W_]", "", str(value).strip().lower())


def _find_snapshot_number(raw: Any, aliases: Set[str]) -> Optional[float]:
    if isinstance(raw, dict):
        for key, value in raw.items():
            if _normalized_snapshot_key(key) in aliases:
                try:
                    number = float(value)
                except (TypeError, ValueError):
                    continue
                if math.isfinite(number) and number > 0:
                    return number
        for value in raw.values():
            found = _find_snapshot_number(value, aliases)
            if found is not None:
                return found
    elif isinstance(raw, (list, tuple)):
        for value in raw:
            found = _find_snapshot_number(value, aliases)
            if found is not None:
                return found
    return None


def _find_snapshot_text(raw: Any, aliases: Set[str]) -> str:
    if isinstance(raw, dict):
        for key, value in raw.items():
            if _normalized_snapshot_key(key) in aliases and isinstance(value, str):
                text = value.strip()
                if text:
                    return text
        for value in raw.values():
            found = _find_snapshot_text(value, aliases)
            if found:
                return found
    elif isinstance(raw, (list, tuple)):
        for value in raw:
            found = _find_snapshot_text(value, aliases)
            if found:
                return found
    return ""


def parse_market_snapshot(raw: Any) -> Optional[MarketSnapshot]:
    """Normalize TQ snapshot field aliases without assuming a fixed client build.
    
    TQ's get_market_snapshot returns fields like Now, Max, Min, LastClose, UpHome.
    UpHome (涨停价) is often 0, so we calculate it from LastClose when needed.
    """
    last_price = _find_snapshot_number(
        raw,
        {
            "last",
            "lastprice",
            "latestprice",
            "price",
            "close",
            "now",
            "newprice",
            "zuixinjia",
            "最新价",
        },
    )
    upper_limit = _find_snapshot_number(
        raw,
        {
            "uplimit",
            "uplimitprice",
            "upperlimit",
            "upperlimitprice",
            "limitup",
            "limitupprice",
            "ztj",
            "ztprice",
            "zhangtingjia",
            "涨停价",
            "uphome",
            "max",  # fallback: use highest price of the day
        },
    )
    
    # If upper_limit is 0 or missing, calculate from LastClose
    if upper_limit is None or upper_limit <= 0:
        last_close = _find_snapshot_number(
            raw,
            {
                "lastclose",
                "prevclose",
                "previousclose",
                "zuoshou",
                "昨收",
                "昨收价",
            },
        )
        if last_close is not None and last_close > 0:
            # Try to determine stock type from symbol if available in raw
            # Default to 10% limit for most A-shares
            upper_limit = last_close * 1.1
    
    if last_price is None or upper_limit is None:
        return None
    stock_name = _find_snapshot_text(
        raw,
        {
            "name",
            "stockname",
            "securityname",
            "securename",
            "zhengquanjiancheng",
            "证券简称",
            "股票简称",
            "名称",
        },
    )
    return MarketSnapshot(last_price=last_price, upper_limit=upper_limit, stock_name=stock_name)


def _snapshot_field_names(raw: Any) -> List[str]:
    fields: Set[str] = set()
    if isinstance(raw, dict):
        for key, value in raw.items():
            fields.add(str(key))
            fields.update(_snapshot_field_names(value))
    elif isinstance(raw, (list, tuple)):
        for value in raw:
            fields.update(_snapshot_field_names(value))
    return sorted(fields)


def fetch_market_snapshots(
    tq: Any, symbols: Sequence[str]
) -> Tuple[Dict[str, MarketSnapshot], List[str]]:
    """Fetch current prices one symbol at a time for the 14:40 limit-up safety check."""
    snapshots: Dict[str, MarketSnapshot] = {}
    errors: List[str] = []
    for symbol in symbols:
        try:
            raw_snapshot = tq.get_market_snapshot(symbol)
            snapshot = parse_market_snapshot(raw_snapshot)
        except Exception as error:
            errors.append(f"{symbol}: 获取14:40市场快照失败: {error}")
            continue
        if snapshot is None:
            fields = ",".join(_snapshot_field_names(raw_snapshot)[:20]) or "无"
            errors.append(f"{symbol}: 市场快照缺少最新价或涨停价，返回字段={fields}")
            continue
        snapshots[symbol] = snapshot
    return snapshots, errors


def fetch_stock_names(
    tq: Any, symbols: Sequence[str], snapshots: Dict[str, MarketSnapshot]
) -> Dict[str, str]:
    """Prefer names already present in snapshots, then fall back to TQ stock info."""
    names = {
        symbol: snapshot.stock_name
        for symbol, snapshot in snapshots.items()
        if snapshot.stock_name
    }
    for symbol in symbols:
        if symbol in names:
            continue
        try:
            name = _find_snapshot_text(
                tq.get_stock_info(symbol),
                {
                    "name",
                    "stockname",
                    "securityname",
                    "securename",
                    "zhengquanjiancheng",
                    "证券简称",
                    "股票简称",
                    "名称",
                },
            )
        except Exception:
            name = ""
        if name:
            names[symbol] = name
    return names


def _latest_change_pct(bars: Sequence[DailyBar]) -> Optional[float]:
    if len(bars) < 2 or bars[-2].close <= 0:
        return None
    return (bars[-1].close / bars[-2].close - 1) * 100


def _amount_ratio(bars: Sequence[DailyBar]) -> Optional[float]:
    if len(bars) < DISTRIBUTION_LOOKBACK + 1:
        return None
    previous_amounts = [bar.amount for bar in bars[-DISTRIBUTION_LOOKBACK - 1 : -1]]
    average_amount = sum(previous_amounts) / len(previous_amounts)
    if average_amount <= 0:
        return None
    return bars[-1].amount / average_amount


def has_unrecovered_distribution_bar(bars: Sequence[DailyBar]) -> bool:
    """Detect a recent high-volume bearish candle whose high has not been reclaimed."""
    if len(bars) < DISTRIBUTION_LOOKBACK + 2:
        return False
    latest_close = bars[-1].close
    start = max(DISTRIBUTION_LOOKBACK, len(bars) - DISTRIBUTION_LOOKBACK - 1)
    for index in range(start, len(bars) - 1):
        bar = bars[index]
        prior_amounts = [item.amount for item in bars[index - DISTRIBUTION_LOOKBACK : index]]
        average_amount = sum(prior_amounts) / len(prior_amounts)
        price_range = bar.high - bar.low
        body_ratio = (bar.open - bar.close) / price_range if price_range > 0 else 0.0
        is_distribution = (
            bar.close < bar.open
            and average_amount > 0
            and bar.amount >= average_amount * 1.5
            and body_ratio >= 0.6
        )
        if is_distribution and latest_close < bar.high:
            return True
    return False


def _rank_component(value: float, values: Sequence[float], weight: float) -> float:
    unique_values = sorted(set(values))
    if not unique_values:
        return 0.0
    if len(unique_values) == 1:
        return weight
    index = unique_values.index(value)
    return weight * index / (len(unique_values) - 1)


def _tail_structure_metrics(bar: DailyBar) -> Tuple[float, float]:
    price_range = bar.high - bar.low
    if price_range <= 0:
        return 0.0, 1.0
    close_position = min(1.0, max(0.0, (bar.close - bar.low) / price_range))
    upper_shadow = min(1.0, max(0.0, (bar.high - max(bar.open, bar.close)) / price_range))
    return close_position, upper_shadow


def _ema(values: Sequence[float], period: int) -> List[float]:
    if period <= 0:
        raise ValueError("EMA 周期必须大于 0")
    result: List[float] = []
    for value in values:
        result.append(value if not result else (2 * value + (period - 1) * result[-1]) / (period + 1))
    return result


def linear_factor_score(value: float, bad: float, good: float, weight: float) -> float:
    """Map a factor linearly between its zero and full-score bounds."""
    if not all(math.isfinite(item) for item in (value, bad, good, weight)):
        return 0.0
    if good <= bad or weight <= 0:
        return 0.0
    ratio = min(1.0, max(0.0, (value - bad) / (good - bad)))
    return weight * ratio


def brick_strength_score(today_red_length: float, yesterday_green_length: float) -> float:
    """Score brick recovery continuously from the confirmed three-quarters gate."""
    if today_red_length <= 0 or yesterday_green_length <= 0:
        return 0.0
    recovery_ratio = today_red_length / yesterday_green_length
    return linear_factor_score(
        recovery_ratio,
        0.75,
        2.0,
        FACTOR_WEIGHTS["brick_strength"],
    )


def b1_trend_component_scores(
    gap_pct: float,
    white_slope_pct: float,
    yellow_slope_pct: float,
    close_gap_pct: float,
) -> Dict[str, float]:
    """Return the four approved continuous white/yellow trend components."""
    weight = FACTOR_WEIGHTS["white_yellow_trend"]
    return {
        "gap": linear_factor_score(gap_pct, -1.0, 1.0, weight * 8 / 17),
        "white_slope": linear_factor_score(white_slope_pct, -0.5, 0.5, weight * 4 / 17),
        "yellow_slope": linear_factor_score(yellow_slope_pct, -0.3, 0.3, weight * 3 / 17),
        "close_gap": linear_factor_score(close_gap_pct, -1.0, 1.0, weight * 2 / 17),
    }


def _formula_periods(formula_arg: str) -> Tuple[int, int, int, int]:
    try:
        values = [int(part.strip()) for part in formula_arg.split(",") if part.strip()]
    except ValueError as error:
        raise ValueError(f"无法解析公式参数: {formula_arg}") from error
    if len(values) < 4 or any(value <= 0 for value in values[:4]):
        raise ValueError(f"公式参数缺少有效的 M1..M4: {formula_arg}")
    return tuple(values[:4])  # type: ignore[return-value]


def b1_trend_metrics(bars: Sequence[DailyBar], formula_arg: str) -> Dict[str, Any]:
    """Calculate ZHUAN's white/yellow trend-line confirmation factor."""
    periods = _formula_periods(formula_arg)
    if len(bars) < max(periods) + 1:
        return {
            "score": 0.0, "white_above_yellow": False, "white_rising": False,
            "yellow_rising": False, "white_line": 0.0, "yellow_line": 0.0,
            "white_yellow_gap_pct": 0.0, "white_slope_pct": 0.0,
            "yellow_slope_pct": 0.0, "close_yellow_gap_pct": 0.0,
            "component_scores": b1_trend_component_scores(0.0, 0.0, 0.0, 0.0),
        }
    closes = [bar.close for bar in bars]
    white = _ema(_ema(closes, 10), 10)

    def yellow_at(index: int) -> float:
        return sum(sum(closes[index - period + 1 : index + 1]) / period for period in periods) / len(periods)

    yellow_now = yellow_at(len(closes) - 1)
    yellow_previous = yellow_at(len(closes) - 2)
    white_now, white_previous = white[-1], white[-2]
    white_above = white_now >= yellow_now
    white_rising = white_now >= white_previous
    yellow_rising = yellow_now >= yellow_previous
    close_above = closes[-1] >= yellow_now
    gap_pct = (white_now / yellow_now - 1) * 100 if yellow_now else 0.0
    white_slope_pct = (white_now / white_previous - 1) * 100 if white_previous else 0.0
    yellow_slope_pct = (yellow_now / yellow_previous - 1) * 100 if yellow_previous else 0.0
    close_gap_pct = (closes[-1] / yellow_now - 1) * 100 if yellow_now else 0.0
    component_scores = b1_trend_component_scores(
        gap_pct, white_slope_pct, yellow_slope_pct, close_gap_pct
    )
    score = sum(component_scores.values())
    return {
        "score": score, "white_above_yellow": white_above, "white_rising": white_rising,
        "yellow_rising": yellow_rising, "white_line": white_now, "yellow_line": yellow_now,
        "white_yellow_gap_pct": gap_pct, "white_slope_pct": white_slope_pct,
        "yellow_slope_pct": yellow_slope_pct, "close_yellow_gap_pct": close_gap_pct,
        "component_scores": component_scores,
    }


def previous_kdj_j_score(previous_j: float) -> float:
    """Score prior-day J continuously, with a steeper decline above 12."""
    if not math.isfinite(previous_j):
        return 0.0
    weight = FACTOR_WEIGHTS["previous_kdj_j"]
    if previous_j <= 0:
        return weight
    if previous_j <= 12:
        return weight * (1.0 - 0.2 * previous_j / 12.0)
    if previous_j < 30:
        return weight * 0.8 * (30.0 - previous_j) / 18.0
    return 0.0


def previous_kdj_j_metrics(bars: Sequence[DailyBar]) -> Dict[str, float]:
    """Return the prior-day default KDJ(9,3,3) J value and oversold score."""
    if len(bars) < 10:
        return {"previous_j": 0.0, "score": 0.0}
    rsv: List[float] = []
    for index in range(8, len(bars)):
        window = bars[index - 8 : index + 1]
        highest = max(bar.high for bar in window)
        lowest = min(bar.low for bar in window)
        rsv.append((bars[index].close - lowest) / (highest - lowest) * 100 if highest > lowest else 50.0)
    k_values = sma(rsv, 3)
    d_values = sma(k_values, 3)
    j_values = [3 * k - 2 * d for k, d in zip(k_values, d_values)]
    previous_j = j_values[-2]
    return {"previous_j": previous_j, "score": previous_kdj_j_score(previous_j)}


def previous_kdj_doji_metrics(bars: Sequence[DailyBar], previous_j: float) -> Dict[str, float]:
    """Reward a small prior-day doji only when it confirms low KDJ J."""
    if len(bars) < 2 or bars[-2].open <= 0:
        return {"previous_doji": 0.0, "previous_body_pct": 0.0, "score": 0.0}
    previous = bars[-2]
    body_pct = abs(previous.close - previous.open) / previous.open * 100
    is_doji = body_pct <= 1.5
    if is_doji and previous_j < 12:
        score = FACTOR_WEIGHTS["previous_kdj_doji"]
    else:
        score = 0.0
    return {"previous_doji": 1.0 if is_doji else 0.0, "previous_body_pct": body_pct, "score": score}


def _consecutive_rising_days(bars: Sequence[DailyBar]) -> int:
    streak = 0
    for index in range(len(bars) - 1, 0, -1):
        if bars[index].close > bars[index - 1].close:
            streak += 1
        else:
            break
    return streak


def _signal_source(symbol: str, native_matches: Set[str], python_matches: Set[str]) -> str:
    if symbol in native_matches and symbol in python_matches:
        return "shared"
    if symbol in native_matches:
        return "native_only"
    if symbol in python_matches:
        return "python_only"
    return "unknown"


def _zero_score(
    symbol: str, stock_name: str, signal_source: str, reasons: Sequence[str]
) -> CandidateScore:
    return CandidateScore(
        symbol=symbol,
        stock_name=stock_name,
        signal_source=signal_source,
        final_score=0.0,
        base_score=0.0,
        risk_penalty=0.0,
        factor_scores={name: 0.0 for name in FACTOR_WEIGHTS},
        hard_filter_reasons=tuple(reasons),
        risk_reasons=(),
        metrics={},
    )


def apply_sector_rank_score_caps(candidates: Sequence[CandidateScore]) -> List[CandidateScore]:
    """Prevent one concept board from monopolizing the global candidate ranking.

    The first three names retain their factor score. Starting from the fourth,
    the score caps decay quickly while candidates remain visible for review.
    """
    capped: List[CandidateScore] = []
    for rank, candidate in enumerate(candidates, start=1):
        cap: Optional[float] = None
        if rank > TOP_CANDIDATES_PER_SECTOR:
            cap = SECTOR_RANK_SCORE_CAPS[min(rank - TOP_CANDIDATES_PER_SECTOR - 1, len(SECTOR_RANK_SCORE_CAPS) - 1)]
        final_score = min(candidate.final_score, cap) if cap is not None else candidate.final_score
        capped.append(
            replace(
                candidate,
                final_score=final_score,
                sector_rank=rank,
                sector_score_cap=cap,
                metrics={**candidate.metrics, "sector_rank": float(rank), "sector_score_cap": cap or 0.0},
            )
        )
    return capped


def rank_sector_candidates(
    sector: str,
    board_rank: int,
    board_count: int,
    candidate_symbols: Sequence[str],
    stock_bars: Dict[str, Sequence[DailyBar]],
    signals: Dict[str, BrickSignal],
    snapshots: Dict[str, MarketSnapshot],
    board_change_pct: float = 0.0,
    native_matches: Optional[Set[str]] = None,
    python_matches: Optional[Set[str]] = None,
    stock_names: Optional[Dict[str, str]] = None,
    formula_arg: str = "14,28,57,114,3,21",
) -> List[CandidateScore]:
    """Rank one concept board while retaining all hard-filtered candidates at zero."""
    native_matches = native_matches or set()
    python_matches = python_matches if python_matches is not None else set(signals)
    stock_names = stock_names or {}
    valid_changes = {
        symbol: _latest_change_pct(bars)
        for symbol, bars in stock_bars.items()
        if _latest_change_pct(bars) is not None
    }
    relative_changes = {
        symbol: change - board_change_pct
        for symbol, change in valid_changes.items()
        if change is not None
    }
    amount_ratios = {
        symbol: ratio
        for symbol, bars in stock_bars.items()
        for ratio in [_amount_ratio(bars)]
        if ratio is not None
    }
    latest_amounts = {
        symbol: bars[-1].amount
        for symbol, bars in stock_bars.items()
        if bars and bars[-1].amount > 0
    }
    board_score = FACTOR_WEIGHTS["board_leadership"] * max(
        0.0, (board_count - board_rank + 1) / max(1, board_count)
    )
    scores: List[CandidateScore] = []
    for symbol in sorted(set(candidate_symbols)):
        bars = stock_bars.get(symbol)
        signal_source = _signal_source(symbol, native_matches, python_matches)
        stock_name = stock_names.get(symbol, "")
        hard_reasons: List[str] = []
        if bars is None:
            hard_reasons.append("daily_data_missing")
        snapshot = snapshots.get(symbol)
        if snapshot is None:
            hard_reasons.append("snapshot_data_missing")
        if bars is not None and has_unrecovered_distribution_bar(bars):
            hard_reasons.append("unrecovered_high_volume_bearish_candle")
        if snapshot is not None and snapshot.last_price >= snapshot.upper_limit - max(0.01, snapshot.upper_limit * 0.0001):
            hard_reasons.append("tail_limit_up_locked")
        if hard_reasons:
            scores.append(_zero_score(symbol, stock_name, signal_source, hard_reasons))
            continue

        assert bars is not None
        assert snapshot is not None
        stock_change = valid_changes.get(symbol)
        amount_ratio = amount_ratios.get(symbol)
        latest_amount = latest_amounts.get(symbol)
        if stock_change is None or amount_ratio is None or latest_amount is None:
            scores.append(_zero_score(symbol, stock_name, signal_source, ["score_data_missing"]))
            continue

        relative_strength = stock_change - board_change_pct
        relative_score = _rank_component(
            relative_strength, list(relative_changes.values()), FACTOR_WEIGHTS["relative_strength"]
        )
        liquidity_score = (
            _rank_component(latest_amount, list(latest_amounts.values()), FACTOR_WEIGHTS["liquidity"] / 2)
            + _rank_component(amount_ratio, list(amount_ratios.values()), FACTOR_WEIGHTS["liquidity"] / 2)
        )
        close_position, upper_shadow = _tail_structure_metrics(bars[-1])
        tail_score = FACTOR_WEIGHTS["tail_structure"] * (
            close_position * 2 / 3 + (1 - upper_shadow) / 3
        )
        try:
            brick_state = latest_brick_state(calculate_brick_series(bars))
        except Exception:
            brick_state = None
        brick_ratio = 0.0
        brick_score = 0.0
        if (
            brick_state is not None
            and brick_state.today_red_length > 0
            and brick_state.yesterday_green_length > 0
        ):
            brick_ratio = brick_state.today_red_length / brick_state.yesterday_green_length
            brick_score = brick_strength_score(
                brick_state.today_red_length,
                brick_state.yesterday_green_length,
            )
        try:
            trend = b1_trend_metrics(bars, formula_arg)
        except ValueError:
            trend = b1_trend_metrics(bars, "14,28,57,114,3,21")
        kdj = previous_kdj_j_metrics(bars)
        kdj_doji = previous_kdj_doji_metrics(bars, kdj["previous_j"])
        factor_scores = {
            "board_leadership": board_score,
            "relative_strength": relative_score,
            "liquidity": liquidity_score,
            "tail_structure": tail_score,
            "brick_strength": brick_score,
            "white_yellow_trend": float(trend["score"]),
            "previous_kdj_j": kdj["score"],
            "previous_kdj_doji": kdj_doji["score"],
        }
        risk_reasons: List[str] = []
        risk_penalty = 0.0
        if stock_change >= 8.0:
            risk_penalty += 8.0
            risk_reasons.append("daily_gain_at_least_8pct")
        elif stock_change >= 5.0:
            risk_penalty += 4.0
            risk_reasons.append("daily_gain_at_least_5pct")
        if upper_shadow >= 0.4:
            risk_penalty += 6.0
            risk_reasons.append("long_upper_shadow")
        if _consecutive_rising_days(bars) >= 4:
            risk_penalty += 6.0
            risk_reasons.append("four_day_rising_streak")
        risk_penalty = min(30.0, risk_penalty)
        base_score = sum(factor_scores.values())
        scores.append(
            CandidateScore(
                symbol=symbol,
                stock_name=stock_name,
                signal_source=signal_source,
                final_score=max(0.0, base_score - risk_penalty),
                base_score=base_score,
                risk_penalty=risk_penalty,
                factor_scores=factor_scores,
                hard_filter_reasons=(),
                risk_reasons=tuple(risk_reasons),
                metrics={
                    "stock_change_pct": stock_change,
                    "relative_strength_pct": relative_strength,
                    "amount_ratio_5d": amount_ratio,
                    "close_position": close_position,
                    "upper_shadow_ratio": upper_shadow,
                    "brick_strength_ratio": brick_ratio,
                    "white_line": float(trend["white_line"]),
                    "yellow_line": float(trend["yellow_line"]),
                    "white_yellow_gap_pct": float(trend["white_yellow_gap_pct"]),
                    "white_slope_pct": float(trend["white_slope_pct"]),
                    "yellow_slope_pct": float(trend["yellow_slope_pct"]),
                    "close_yellow_gap_pct": float(trend["close_yellow_gap_pct"]),
                    "white_above_yellow": 1.0 if trend["white_above_yellow"] else 0.0,
                    "white_rising": 1.0 if trend["white_rising"] else 0.0,
                    "yellow_rising": 1.0 if trend["yellow_rising"] else 0.0,
                    "previous_kdj_j": kdj["previous_j"],
                    "previous_doji": kdj_doji["previous_doji"],
                    "previous_body_pct": kdj_doji["previous_body_pct"],
                    "snapshot_last_price": snapshot.last_price,
                    "snapshot_upper_limit": snapshot.upper_limit,
                },
            )
        )
    return sorted(scores, key=lambda item: (-item.final_score, item.symbol))


def compare_matches(native: Set[str], python: Set[str]) -> Dict[str, List[str]]:
    """Return stable comparison buckets for the two engines."""
    return {
        "shared": sorted(native & python),
        "native_only": sorted(native - python),
        "python_only": sorted(python - native),
    }


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="通达信砖型图尾盘概念板块筛选器")
    parser.add_argument(
        "--tdx-path",
        default=os.environ.get("TDX_PATH", r"F:\new_tdx64"),
        help="通达信安装目录",
    )
    parser.add_argument("--formula-name", default="ZHUAN", help="通达信已保存选股公式名称")
    parser.add_argument(
        "--formula-arg",
        default="14,28,57,114,3,21",
        help="公式参数，逗号分隔 (M1,M2,M3,M4,N1,N2)",
    )
    parser.add_argument(
        "--engine",
        choices=("native", "python", "both"),
        default="both",
        help="筛选引擎",
    )
    parser.add_argument("--concept-limit", type=int, default=5, help="概念板块数量")
    parser.add_argument("--bar-count", type=int, default=130, help="Python 模式日线数量，至少覆盖黄线 MA114")
    parser.add_argument("--batch-size", type=int, default=200, help="批量调用证券数量")
    parser.add_argument("--run-time", default="14:40", help="预期运行时间，仅用于结果记录")
    parser.add_argument("--diagnose", action="store_true", help="仅输出概念板块数据诊断信息")
    parser.add_argument("--preflight", action="store_true", help="仅检查 TQ 与当日概念日线是否就绪")
    parser.add_argument("--json", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--output", help="Markdown 报告路径；默认按日期写入 reports 目录")
    parser.add_argument("--debug-native", action="store_true", help="调试原生公式返回结构")
    return parser.parse_args(argv)


def debug_native_formula(
    tq: Any, formula_name: str, symbols: List[str], formula_arg: str = ""
) -> None:
    """Print raw TQ formula response for debugging."""
    import json as json_mod
    print(f"\n=== 调试原生公式: {formula_name} ===")
    print(f"测试股票列表: {symbols[:5]}... (共{len(symbols)}只)")
    try:
        kwargs = {
            "formula_name": formula_name,
            "stock_list": symbols[:5],
            "stock_period": "1d",
            "return_count": 1,
        }
        if formula_arg:
            kwargs["formula_arg"] = formula_arg
        raw = tq.formula_process_mul_xg(**kwargs)
        print(f"\n原始返回类型: {type(raw).__name__}")
        if isinstance(raw, dict):
            print(f"顶层键: {list(raw.keys())}")
            for key, value in raw.items():
                print(f"\n键 '{key}':")
                print(f"  类型: {type(value).__name__}")
                if isinstance(value, dict):
                    print(f"  子键: {list(value.keys())[:10]}...")
                    for sub_key, sub_value in list(value.items())[:3]:
                        print(f"    '{sub_key}': {type(sub_value).__name__} = {repr(sub_value)[:100]}")
                elif isinstance(value, (list, tuple)):
                    print(f"  长度: {len(value)}")
                    for i, item in enumerate(value[:3]):
                        print(f"    [{i}]: {type(item).__name__} = {repr(item)[:100]}")
                else:
                    print(f"  值: {repr(value)[:200]}")
        else:
            print(f"原始值: {repr(raw)[:500]}")
    except Exception as error:
        print(f"调用失败: {error}")
    print("=== 调试结束 ===\n")


def _serialize_selection(result: Optional[SelectionResult]) -> Dict[str, Any]:
    if result is None:
        return {"matches": [], "signals": {}, "errors": []}
    return {
        "matches": sorted(result.matches),
        "signals": {symbol: asdict(signal) for symbol, signal in sorted(result.signals.items())},
        "errors": list(result.errors),
    }


def _current_date() -> str:
    return datetime.now().strftime("%Y-%m-%d")


def _default_report_path(data_date: str) -> Path:
    project_root = Path(__file__).resolve().parents[3]
    return project_root / "data" / "tdx-brick-selector" / "reports" / f"{data_date}.md"


def _write_output(path: Path, output: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(output if output.endswith("\n") else output + "\n", encoding="utf-8")


def build_preflight_payload(args: argparse.Namespace, tq: Any) -> Dict[str, Any]:
    """Check only TQ concept-board availability before a scheduled selector run."""
    diagnostics = diagnose_concepts(
        tq,
        _current_date(),
        args.batch_size,
        args.tdx_path,
        minimum_concept_count=args.concept_limit,
    )
    ready = (
        not diagnostics["errors"]
        and diagnostics["current_day_concept_count"] >= args.concept_limit
    )
    return {
        "ready": ready,
        "reason": "ready" if ready else "concept_data_unavailable",
        "concept_limit": args.concept_limit,
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        **diagnostics,
    }


def build_run_payload(args: argparse.Namespace, tq: Any) -> Dict[str, Any]:
    started = time.perf_counter()
    stages: Dict[str, float] = {}

    stage_started = time.perf_counter()
    concept_codes = fetch_concept_boards(tq, args.tdx_path)
    sector_names = fetch_sector_names(tq)
    requested_date = _current_date()
    board_bars, board_errors = fetch_board_bars(
        tq, concept_codes, count=2, batch_size=args.batch_size
    )
    stages["concepts"] = round(time.perf_counter() - stage_started, 4)
    data_date = resolve_latest_concept_date(board_bars, args.concept_limit)
    if data_date is None:
        raise RuntimeError(f"最近交易日有效概念板块不足 {args.concept_limit} 个")
    ranked_concepts = select_top_concepts(board_bars, args.concept_limit, data_date)
    selected_sector_codes = [code for code, _ in ranked_concepts]

    stage_started = time.perf_counter()
    sector_members = fetch_sector_members(tq, selected_sector_codes)
    symbols = sorted(sector_members)
    stages["members"] = round(time.perf_counter() - stage_started, 4)

    native_result: Optional[SelectionResult] = None
    python_result: Optional[SelectionResult] = None
    errors = list(board_errors)

    # Ranking always requires strict Python signals and OHLCVA fields, even in native-only mode.
    stage_started = time.perf_counter()
    stock_bars, stock_errors = fetch_board_bars(
        tq, symbols, count=args.bar_count, batch_size=args.batch_size
    )
    strict_python_result = run_python_selection(stock_bars, data_date)
    strict_python_result.errors[:0] = stock_errors
    stages["python"] = round(time.perf_counter() - stage_started, 4)
    errors.extend(strict_python_result.errors)
    if args.engine in ("python", "both"):
        python_result = strict_python_result

    if args.engine in ("native", "both"):
        stage_started = time.perf_counter()
        native_result = run_native_selection(
            tq, symbols, args.formula_name, args.batch_size,
            formula_arg=getattr(args, "formula_arg", "")
        )
        stages["native"] = round(time.perf_counter() - stage_started, 4)

    candidate_symbols = sorted(
        (native_result.matches if native_result else set()) | strict_python_result.matches
    )
    stage_started = time.perf_counter()
    snapshots, snapshot_errors = fetch_market_snapshots(tq, candidate_symbols)
    stages["snapshots"] = round(time.perf_counter() - stage_started, 4)
    stage_started = time.perf_counter()
    stock_names = fetch_stock_names(tq, candidate_symbols, snapshots)
    stages["names"] = round(time.perf_counter() - stage_started, 4)

    ranking: List[Dict[str, Any]] = []
    for board_rank, (sector, board_change_pct) in enumerate(ranked_concepts, start=1):
        sector_stock_bars = {
            symbol: bars
            for symbol, bars in stock_bars.items()
            if sector in sector_members.get(symbol, [])
        }
        sector_candidates = [
            symbol for symbol in candidate_symbols if sector in sector_members.get(symbol, [])
        ]
        ranked_candidates = apply_sector_rank_score_caps(rank_sector_candidates(
            sector=sector,
            board_rank=board_rank,
            board_count=len(ranked_concepts),
            candidate_symbols=sector_candidates,
            stock_bars=sector_stock_bars,
            signals=strict_python_result.signals,
            snapshots=snapshots,
            board_change_pct=board_change_pct,
            native_matches=native_result.matches if native_result else set(),
            python_matches=strict_python_result.matches,
            stock_names=stock_names,
            formula_arg=args.formula_arg,
        ))
        ranking.append(
            {
                "sector": sector,
                "sector_name": sector_names.get(sector, ""),
                "board_rank": board_rank,
                "board_change_pct": board_change_pct,
                "candidates": [
                    {
                        **asdict(candidate),
                        "rank": candidate.sector_rank,
                        "top_three": candidate.sector_rank <= TOP_CANDIDATES_PER_SECTOR,
                    }
                    for candidate in ranked_candidates
                ],
            }
        )

    payload: Dict[str, Any] = {
        "ok": True,
        "run_time": args.run_time,
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "requested_date": requested_date,
        "data_date": data_date,
        "fallback_used": data_date != requested_date,
        "engine": args.engine,
        "formula_name": args.formula_name,
        "formula_arg": getattr(args, "formula_arg", ""),
        "tdx_path": args.tdx_path,
        "concept_limit": args.concept_limit,
        "concepts": [
            {"symbol": symbol, "name": sector_names.get(symbol, ""), "change_pct": change_pct}
            for symbol, change_pct in ranked_concepts
        ],
        "sector_names": sector_names,
        "sector_members": sector_members,
        "member_count": len(symbols),
        "native": _serialize_selection(native_result),
        "python": _serialize_selection(python_result),
        "comparison": compare_matches(
            native_result.matches if native_result else set(),
            python_result.matches if python_result else set(),
        ),
        "ranking": ranking,
        "errors": errors
        + snapshot_errors
        + (native_result.errors if native_result else []),
        "stages_seconds": {
            **stages,
            "total": round(time.perf_counter() - started, 4),
        },
    }
    payload["execution_candidates"] = select_execution_candidates(payload)
    return payload


_SOURCE_LABELS = {
    "shared": "双引擎",
    "native_only": "原生",
    "python_only": "Python",
    "unknown": "未知",
}
_REASON_LABELS = {
    "daily_data_missing": "日线缺失",
    "snapshot_data_missing": "快照缺失",
    "score_data_missing": "评分数据缺失",
    "tail_limit_up_locked": "尾盘封涨停",
    "unrecovered_high_volume_bearish_candle": "未收复放量阴线",
    "daily_gain_at_least_8pct": "当日涨幅≥8%",
    "daily_gain_at_least_5pct": "当日涨幅≥5%",
    "long_upper_shadow": "长上影",
    "four_day_rising_streak": "连续上涨≥4日",
}


def _markdown_escape(value: Any) -> str:
    return str(value).replace("|", "\\|").replace("\n", " ").strip()


def _sector_label(code: str, sector_names: Dict[str, str]) -> str:
    name = sector_names.get(code, "")
    return f"{name} ({code})" if name else code


def _reason_labels(reasons: Sequence[str]) -> str:
    return "、".join(_REASON_LABELS.get(reason, reason) for reason in reasons) or "-"


def _global_ranked_candidates(payload: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Keep one row per stock, selecting its best board-context score."""
    best_by_symbol: Dict[str, Dict[str, Any]] = {}
    for board in payload.get("ranking", []):
        for candidate in board.get("candidates", []):
            symbol = candidate["symbol"]
            entry = {**candidate, "best_sector": board["sector"], "best_sector_name": board.get("sector_name", "")}
            previous = best_by_symbol.get(symbol)
            if previous is None or (
                entry["final_score"], -board.get("board_rank", 0)
            ) > (
                previous["final_score"], -previous.get("board_rank", 0)
            ):
                entry["board_rank"] = board.get("board_rank", 0)
                best_by_symbol[symbol] = entry
    return sorted(
        best_by_symbol.values(),
        key=lambda item: (-item["final_score"], item["symbol"]),
    )


def select_execution_candidates(
    payload: Dict[str, Any], limit: int = EXECUTION_TOP_LIMIT
) -> List[Dict[str, Any]]:
    """Return the positive dual-engine candidates eligible for execution ranking."""
    if limit <= 0:
        return []
    selected: List[Dict[str, Any]] = []
    for candidate in _global_ranked_candidates(payload):
        try:
            score = float(candidate.get("final_score", 0.0))
        except (TypeError, ValueError):
            continue
        if candidate.get("signal_source") != "shared" or not math.isfinite(score) or score <= 0:
            continue
        selected.append(candidate)
        if len(selected) >= limit:
            break
    return selected


def render_markdown_report(payload: Dict[str, Any]) -> str:
    """Render the final, globally ranked user-facing daily selection report."""
    sector_names = payload.get("sector_names", {})
    lines = [
        "# 通达信尾盘候选排序",
        "",
        f"- 数据日期：{payload['data_date']}",
        f"- 运行时点：{payload['run_time']}",
        f"- 公式：{payload['formula_name']}（{payload.get('formula_arg', '默认参数')}）",
        "- 执行候选：仅双引擎共振的正分 Top 3；单端信号仅作观察。",
        "",
        "## 前五概念板块",
        "",
        "| 排名 | 概念板块 | 涨幅 |",
        "| ---: | --- | ---: |",
    ]
    if payload.get("fallback_used"):
        lines.insert(3, f"- 自然日：{payload.get('requested_date')}（自动回退至最近交易日）")
    for rank, concept in enumerate(payload.get("concepts", []), start=1):
        label = _sector_label(concept["symbol"], sector_names)
        lines.append(f"| {rank} | {_markdown_escape(label)} | {concept['change_pct']:.2f}% |")

    execution_candidates = payload.get("execution_candidates")
    if execution_candidates is None:
        execution_candidates = select_execution_candidates(payload)
    lines.extend(
        [
            "",
            "## 执行候选（双引擎共振 Top 3）",
            "",
            "| 排名 | 代码 | 名称 | 最高评分板块 | 总分 | 板内名次 |",
            "| ---: | --- | --- | --- | ---: | ---: |",
        ]
    )
    for rank, candidate in enumerate(execution_candidates, start=1):
        best_sector = _sector_label(candidate.get("best_sector", ""), sector_names)
        lines.append(
            "| {rank} | {symbol} | {name} | {best_sector} | {score:.2f} | {sector_rank} |".format(
                rank=rank,
                symbol=candidate.get("symbol", "-"),
                name=_markdown_escape(candidate.get("stock_name") or "未知"),
                best_sector=_markdown_escape(best_sector),
                score=float(candidate.get("final_score", 0.0)),
                sector_rank=candidate.get("sector_rank") or candidate.get("rank") or "-",
            )
        )
    if not execution_candidates:
        lines.append("| - | - | - | 本次无双引擎共振正分候选 | - | - |")

    lines.extend(
        [
            "",
            "## 观察候选总表（含单端信号）",
            "",
            "| 排名 | 代码 | 名称 | 来源 | 最高评分板块 | 所属概念板块 | 总分 | 板内名次 | 分数上限 | 板块 | 相对 | 量能 | 砖 | 白黄趋势 | 前日J | J+十字 | 扣分 | 风险/归零原因 |",
            "| ---: | --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |",
        ]
    )
    rows = _global_ranked_candidates(payload)
    for rank, candidate in enumerate(rows, start=1):
        factors = candidate.get("factor_scores", {})
        sectors = payload.get("sector_members", {}).get(candidate["symbol"], [])
        sectors_label = "、".join(_sector_label(code, sector_names) for code in sectors) or "-"
        reasons = list(candidate.get("hard_filter_reasons", [])) + list(candidate.get("risk_reasons", []))
        best_sector = _sector_label(candidate["best_sector"], sector_names)
        lines.append(
            "| {rank} | {symbol} | {name} | {source} | {best_sector} | {sectors} | {score:.2f} | "
            "{sector_rank} | {score_cap} | {board:.1f} | {relative:.1f} | {liquidity:.1f} | {brick:.1f} | {trend:.1f} | {kdj:.1f} | {kdj_doji:.1f} | {penalty:.1f} | {reasons} |".format(
                rank=rank,
                symbol=candidate["symbol"],
                name=_markdown_escape(candidate.get("stock_name") or "未知"),
                source=_SOURCE_LABELS.get(candidate.get("signal_source"), candidate.get("signal_source", "未知")),
                best_sector=_markdown_escape(best_sector),
                sectors=_markdown_escape(sectors_label),
                score=candidate["final_score"],
                sector_rank=candidate.get("sector_rank") or candidate.get("rank") or "-",
                score_cap=(f"{candidate['sector_score_cap']:.0f}" if candidate.get("sector_score_cap") is not None else "-"),
                board=factors.get("board_leadership", 0.0),
                relative=factors.get("relative_strength", 0.0),
                liquidity=factors.get("liquidity", 0.0),
                brick=factors.get("brick_strength", 0.0),
                trend=factors.get("white_yellow_trend", 0.0),
                kdj=factors.get("previous_kdj_j", 0.0),
                kdj_doji=factors.get("previous_kdj_doji", 0.0),
                penalty=candidate.get("risk_penalty", 0.0),
                reasons=_markdown_escape(_reason_labels(reasons)),
            )
        )
    if not rows:
        lines.append("| - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | 无候选 |")
    if payload.get("errors"):
        lines.extend(["", "## 数据提示", ""])
        lines.extend(f"- {_markdown_escape(error)}" for error in payload["errors"])
    return "\n".join(lines) + "\n"


def render_payload(payload: Dict[str, Any], as_json: bool) -> str:
    if as_json:
        return json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True, allow_nan=False)
    return render_markdown_report(payload)


def render_concept_diagnostics(diagnostics: Dict[str, Any], as_json: bool) -> str:
    if as_json:
        return json.dumps(diagnostics, ensure_ascii=False, indent=2, sort_keys=True)
    lines = [
        "通达信概念板块数据诊断",
        f"自然日: {diagnostics['requested_date']}",
        f"实际数据日: {diagnostics['data_date'] or '无'}",
        f"全部板块数: {diagnostics['all_sector_count']}",
        f"8805 概念代码数: {diagnostics['concept_code_count']}",
        f"成功取得日线数: {diagnostics['daily_bar_count']}",
        f"实际数据日有效数: {diagnostics['current_day_concept_count']}",
        f"最新日线日期分布: {diagnostics['latest_date_counts']}",
        f"板块代码前缀分布: {diagnostics['sector_prefix_counts']}",
        f"全部板块样本: {', '.join(diagnostics['all_sector_samples']) or '无'}",
        f"概念代码样本: {', '.join(diagnostics['concept_code_samples']) or '无'}",
    ]
    if diagnostics["errors"]:
        lines.append("取数错误:")
        lines.extend(f"  - {error}" for error in diagnostics["errors"])
    return "\n".join(lines)


def render_preflight_payload(payload: Dict[str, Any], as_json: bool) -> str:
    if as_json:
        return json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True, allow_nan=False)
    return "\n".join(
        [
            "通达信筛选预检",
            f"就绪: {'是' if payload['ready'] else '否'}",
            f"原因: {payload['reason']}",
            f"实际数据日: {payload['data_date'] or '无'}",
            f"当前日期有效概念板块: {payload['current_day_concept_count']}/{payload['concept_limit']}",
            f"日线错误数量: {len(payload['errors'])}",
        ]
    )


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    if args.concept_limit <= 0 or args.bar_count < 115 or args.batch_size <= 0:
        print("概念板块数量必须大于 0，日线数量至少为 115（覆盖黄线 MA114），批次大小必须大于 0", file=sys.stderr)
        return 2

    tq = None
    try:
        tq = load_tq(args.tdx_path)
        if args.preflight:
            preflight = build_preflight_payload(args, tq)
            output = render_preflight_payload(preflight, args.json)
            if args.output:
                _write_output(Path(args.output), output)
            print(output)
            return 0 if preflight["ready"] else 1
        if args.diagnose:
            diagnostics = diagnose_concepts(tq, _current_date(), args.batch_size, args.tdx_path)
            output = render_concept_diagnostics(diagnostics, args.json)
            if args.output:
                _write_output(Path(args.output), output)
            print(output)
            return 0
        if args.debug_native:
            # 获取概念板块成分股用于调试
            concept_codes = fetch_concept_boards(tq, args.tdx_path)
            board_bars, _ = fetch_board_bars(tq, concept_codes, count=2, batch_size=args.batch_size)
            data_date = resolve_latest_concept_date(board_bars, args.concept_limit)
            if data_date is None:
                raise RuntimeError(f"最近交易日有效概念板块不足 {args.concept_limit} 个")
            ranked_concepts = select_top_concepts(board_bars, args.concept_limit, data_date)
            selected_sector_codes = [code for code, _ in ranked_concepts]
            sector_members = fetch_sector_members(tq, selected_sector_codes)
            debug_symbols = sorted(sector_members)[:5]
            debug_native_formula(tq, args.formula_name, debug_symbols, args.formula_arg)
            return 0
        payload = build_run_payload(args, tq)
        output = render_payload(payload, args.json)
        report_path = Path(args.output) if args.output else _default_report_path(payload["data_date"])
        _write_output(report_path, output)
        print(output)
        return 0 if payload["ok"] else 1
    except Exception as error:
        print(f"通达信砖型图筛选失败: {error}", file=sys.stderr)
        return 1
    finally:
        if tq is not None and hasattr(tq, "close"):
            tq.close()


if __name__ == "__main__":
    raise SystemExit(main())
