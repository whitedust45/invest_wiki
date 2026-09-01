#!/usr/bin/env python3
"""Historical scoring validation for the TDX brick strategy."""

from __future__ import annotations

import argparse
from collections import defaultdict
from concurrent.futures import ProcessPoolExecutor
import csv
from dataclasses import asdict, dataclass
from datetime import datetime, timedelta
import math
import os
from pathlib import Path
from statistics import mean, median
import sys
import time
from typing import Any, Dict, List, Optional, Sequence, Set, Tuple

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.short_term.strategies.brick import (
    FACTOR_WEIGHTS,
    BrickSignal,
    DailyBar,
    MarketSnapshot,
    _find_field_frames,
    _normalize_symbol_bars,
    apply_sector_rank_score_caps,
    calculate_brick_series,
    chunks,
    fetch_concept_boards,
    fetch_sector_members,
    fetch_sector_names,
    fetch_stock_names,
    load_tq,
    rank_sector_candidates,
    strict_brick_signal,
)
from modules.short_term.backtest_cache import (
    DEFAULT_BACKTEST_CACHE_DB,
    BacktestBarCache,
    CacheStats,
    dynamic_batch_size,
)


FORWARD_HORIZONS = (1, 2, 3, 5)


class HistoricalBarWindow(Sequence[DailyBar]):
    """Read-only prefix view over a stock's bars without copying its history."""

    def __init__(self, bars: Sequence[DailyBar], end_index: int) -> None:
        self._bars = bars
        self._end_index = min(end_index, len(bars) - 1)

    def __len__(self) -> int:
        return max(0, self._end_index + 1)

    def __getitem__(self, index: Any) -> Any:
        length = len(self)
        if isinstance(index, slice):
            start, stop, step = index.indices(length)
            return [self._bars[position] for position in range(start, stop, step)]
        position = int(index)
        if position < 0:
            position += length
        if position < 0 or position >= length:
            raise IndexError("历史K线窗口索引越界")
        return self._bars[position]


@dataclass(frozen=True)
class ReplayContext:
    concept_rankings: Dict[str, List[Tuple[str, float]]]
    sector_members: Dict[str, List[str]]
    members_by_sector: Dict[str, Tuple[str, ...]]
    stock_bars: Dict[str, Sequence[DailyBar]]
    date_indexes: Dict[str, Dict[str, int]]
    native_matches: Dict[str, Set[str]]
    python_matches: Dict[str, Set[str]]
    python_signals: Dict[Tuple[str, str], BrickSignal]
    sources: Dict[str, Dict[str, str]]
    stock_names: Dict[str, str]
    sector_names: Dict[str, str]
    formula_arg: str


def build_replay_context(
    concept_rankings: Dict[str, List[Tuple[str, float]]],
    sector_members: Dict[str, List[str]],
    stock_bars: Dict[str, Sequence[DailyBar]],
    native_matches: Dict[str, Set[str]],
    python_matches: Dict[str, Set[str]],
    python_signals: Dict[Tuple[str, str], BrickSignal],
    sources: Dict[str, Dict[str, str]],
    stock_names: Dict[str, str],
    sector_names: Dict[str, str],
    formula_arg: str,
) -> ReplayContext:
    """Precompute immutable lookup tables used by each historical replay date."""
    by_sector: Dict[str, List[str]] = defaultdict(list)
    for symbol, sectors in sector_members.items():
        if symbol not in stock_bars:
            continue
        for sector in sectors:
            by_sector[sector].append(symbol)
    members_by_sector = {
        sector: tuple(sorted(symbols)) for sector, symbols in sorted(by_sector.items())
    }
    date_indexes = {
        symbol: {bar.date: index for index, bar in enumerate(bars)}
        for symbol, bars in stock_bars.items()
    }
    return ReplayContext(
        concept_rankings=concept_rankings,
        sector_members=sector_members,
        members_by_sector=members_by_sector,
        stock_bars=stock_bars,
        date_indexes=date_indexes,
        native_matches=native_matches,
        python_matches=python_matches,
        python_signals=python_signals,
        sources=sources,
        stock_names=stock_names,
        sector_names=sector_names,
        formula_arg=formula_arg,
    )


def _normalize_history_date(value: Any) -> Optional[str]:
    text = str(value).strip().replace("-", "").replace("/", "")
    if len(text) >= 8 and text[:8].isdigit():
        return f"{text[:4]}-{text[4:6]}-{text[6:8]}"
    return None


def _formula_value_is_true(value: Any) -> bool:
    if isinstance(value, str):
        token = value.strip().lower()
        if token in {"", "0", "false", "no", "n"}:
            return False
        if token in {"1", "true", "yes", "y"}:
            return True
        try:
            return float(token) != 0.0
        except ValueError:
            return False
    if isinstance(value, (int, float)):
        return math.isfinite(float(value)) and float(value) != 0.0
    return False


def extract_historical_formula_matches(
    raw: Any, candidates: Sequence[str]
) -> Dict[str, Set[str]]:
    """Extract date-indexed XG matches from TQ batch formula history."""
    if not isinstance(raw, dict):
        return {}
    candidate_set = {symbol.strip().upper() for symbol in candidates}
    matches: Dict[str, Set[str]] = defaultdict(set)
    for raw_symbol, payload in raw.items():
        symbol = str(raw_symbol).strip().upper()
        if symbol not in candidate_set or not isinstance(payload, dict):
            continue
        xg_values = next(
            (value for key, value in payload.items() if str(key).strip().lower() == "xg"),
            None,
        )
        if not isinstance(xg_values, (list, tuple)):
            continue
        for entry in xg_values:
            if not isinstance(entry, dict):
                continue
            date = next(
                (_normalize_history_date(value) for key, value in entry.items() if str(key).strip().lower() == "date"),
                None,
            )
            value = next(
                (value for key, value in entry.items() if str(key).strip().lower() == "value"),
                None,
            )
            if date and _formula_value_is_true(value):
                matches[date].add(symbol)
    return {date: set(symbols) for date, symbols in sorted(matches.items())}


def historical_python_signals(
    stock_bars: Dict[str, Sequence[DailyBar]],
) -> Tuple[Dict[str, Set[str]], Dict[Tuple[str, str], BrickSignal]]:
    """Calculate strict Python brick signals for every available historical date."""
    matches: Dict[str, Set[str]] = defaultdict(set)
    signals: Dict[Tuple[str, str], BrickSignal] = {}
    for symbol, bars in stock_bars.items():
        bricks = calculate_brick_series(bars)
        for brick_index in range(2, len(bricks)):
            bar_index = brick_index + 3
            if bar_index >= len(bars):
                break
            signal = strict_brick_signal(bricks[brick_index - 2 : brick_index + 1])
            if signal is None:
                continue
            date = bars[bar_index].date
            normalized_symbol = symbol.strip().upper()
            matches[date].add(normalized_symbol)
            signals[(date, normalized_symbol)] = signal
    return {date: set(symbols) for date, symbols in sorted(matches.items())}, signals


def signal_sources_by_date(
    native_matches: Dict[str, Set[str]], python_matches: Dict[str, Set[str]]
) -> Dict[str, Dict[str, str]]:
    """Merge historical engine signals while preserving source provenance."""
    result: Dict[str, Dict[str, str]] = {}
    for date in sorted(set(native_matches) | set(python_matches)):
        native = native_matches.get(date, set())
        python = python_matches.get(date, set())
        result[date] = {
            symbol: (
                "shared" if symbol in native and symbol in python
                else "native_only" if symbol in native
                else "python_only"
            )
            for symbol in sorted(native | python)
        }
    return result


def select_top_positive(rows: Sequence[Dict[str, Any]], limit: int = 5) -> List[Dict[str, Any]]:
    """Deduplicate by symbol and retain only the highest positive global scores."""
    if limit <= 0:
        return []
    best_by_symbol: Dict[str, Dict[str, Any]] = {}
    for raw_row in rows:
        symbol = str(raw_row.get("symbol", "")).strip().upper()
        try:
            score = float(raw_row.get("final_score", 0.0))
        except (TypeError, ValueError):
            continue
        if not symbol or not math.isfinite(score) or score <= 0:
            continue
        row = dict(raw_row)
        row["symbol"] = symbol
        row["final_score"] = score
        previous = best_by_symbol.get(symbol)
        if previous is None or (
            score,
            -int(row.get("board_rank", 0) or 0),
        ) > (
            float(previous["final_score"]),
            -int(previous.get("board_rank", 0) or 0),
        ):
            best_by_symbol[symbol] = row
    ranked = sorted(
        best_by_symbol.values(),
        key=lambda row: (-float(row["final_score"]), str(row["symbol"])),
    )[:limit]
    return [{**row, "rank": rank} for rank, row in enumerate(ranked, start=1)]


def select_consensus_picks(rows: Sequence[Dict[str, Any]], limit: int = 5) -> List[Dict[str, Any]]:
    """Independently rank only candidates confirmed by both selection engines."""
    return select_top_positive(
        [row for row in rows if row.get("signal_source") == "shared"], limit=limit
    )


def forward_outcomes(bars: Sequence[DailyBar], signal_index: int) -> Dict[str, Any]:
    """Measure close-to-close returns and next-day excursions from a signal bar."""
    result: Dict[str, Any] = {
        f"return_{horizon}d": None for horizon in FORWARD_HORIZONS
    }
    result.update(
        {
            "hit_plus_3pct_next_day": None,
            "hit_minus_3pct_next_day": None,
            "next_day_high_return": None,
            "next_day_low_return": None,
        }
    )
    if signal_index < 0 or signal_index >= len(bars):
        return result
    base_close = bars[signal_index].close
    if not math.isfinite(base_close) or base_close <= 0:
        return result
    for horizon in FORWARD_HORIZONS:
        future_index = signal_index + horizon
        if future_index < len(bars):
            result[f"return_{horizon}d"] = bars[future_index].close / base_close - 1.0
    if signal_index + 1 < len(bars):
        next_bar = bars[signal_index + 1]
        high_return = next_bar.high / base_close - 1.0
        low_return = next_bar.low / base_close - 1.0
        result.update(
            {
                "hit_plus_3pct_next_day": high_return >= 0.03,
                "hit_minus_3pct_next_day": low_return <= -0.03,
                "next_day_high_return": high_return,
                "next_day_low_return": low_return,
            }
        )
    return result


def _finite_number(value: Any) -> Optional[float]:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def summarize_cohort(picks: Sequence[Dict[str, Any]], top_n: int) -> Dict[str, Any]:
    """Summarize an equal-weight Top-N basket by signal date."""
    eligible = [row for row in picks if 1 <= int(row.get("rank", 0) or 0) <= top_n]
    dates = sorted({str(row.get("date", "")) for row in eligible if row.get("date")})
    summary: Dict[str, Any] = {"top_n": top_n, "signal_days": len(dates), "picks": len(eligible)}
    for horizon in FORWARD_HORIZONS:
        key = f"return_{horizon}d"
        daily_returns: List[float] = []
        for date in dates:
            values = [
                number
                for row in eligible
                if row.get("date") == date
                for number in [_finite_number(row.get(key))]
                if number is not None
            ]
            if values:
                daily_returns.append(mean(values))
        summary[key] = {
            "sample_days": len(daily_returns),
            "mean": mean(daily_returns) if daily_returns else None,
            "median": median(daily_returns) if daily_returns else None,
            "positive_ratio": (
                sum(value > 0 for value in daily_returns) / len(daily_returns)
                if daily_returns else None
            ),
        }
    for key in ("hit_plus_3pct_next_day", "hit_minus_3pct_next_day"):
        values = [row.get(key) for row in eligible if isinstance(row.get(key), bool)]
        summary[key] = sum(values) / len(values) if values else None
    return summary


def _compact_date(value: str) -> str:
    return value.replace("-", "")


def fetch_historical_bars(
    tq: Any,
    symbols: Sequence[str],
    start_date: str,
    end_date: str,
    batch_size: int,
    cache: Optional[BacktestBarCache] = None,
    refresh_cache: bool = False,
) -> Tuple[Dict[str, List[DailyBar]], List[str], CacheStats]:
    """Load unadjusted TDX daily bars, fetching only cache gaps when available."""
    normalized_symbols = sorted(
        {str(symbol).strip().upper() for symbol in symbols if str(symbol).strip()}
    )
    bars: Dict[str, List[DailyBar]] = {}
    errors: List[str] = []
    cache_read_seconds = 0.0
    tq_fetch_seconds = 0.0
    cached_bar_count = 0
    fetched_bar_count = 0
    fetch_requests = 0

    if cache is not None:
        cache_started = time.perf_counter()
        cached_bars = cache.load_bars(normalized_symbols, start_date, end_date)
        missing_by_range = cache.missing_ranges(
            normalized_symbols, start_date, end_date, refresh=refresh_cache
        )
        cache_read_seconds += time.perf_counter() - cache_started
        cached_bar_count = sum(len(value) for value in cached_bars.values())
        fetch_symbols = {
            symbol for values in missing_by_range.values() for symbol in values
        }
        cache_hit_symbols = len(normalized_symbols) - len(fetch_symbols)
    else:
        missing_by_range = {(start_date, end_date): normalized_symbols}
        fetch_symbols = set(normalized_symbols)
        cache_hit_symbols = 0

    for (range_start, range_end), missing_symbols in missing_by_range.items():
        safe_batch_size = dynamic_batch_size(range_start, range_end, batch_size)
        for batch in chunks(missing_symbols, safe_batch_size):
            fetch_started = time.perf_counter()
            try:
                frames = _find_field_frames(
                    tq.get_market_data(
                        field_list=["Open", "High", "Low", "Close", "Volume", "Amount"],
                        stock_list=batch,
                        period="1d",
                        count=0,
                        start_time=_compact_date(range_start),
                        end_time=_compact_date(range_end),
                        dividend_type="none",
                        fill_data=False,
                    )
                )
            except Exception as error:
                tq_fetch_seconds += time.perf_counter() - fetch_started
                fetch_requests += 1
                errors.append(f"{', '.join(batch)}: 获取历史日线失败: {error}")
                continue
            tq_fetch_seconds += time.perf_counter() - fetch_started
            fetch_requests += 1
            normalized_batch: Dict[str, List[DailyBar]] = {}
            for symbol in batch:
                try:
                    normalized = _normalize_symbol_bars(frames, symbol)
                    if not normalized:
                        raise ValueError("历史日线为空")
                    normalized_batch[symbol] = normalized
                    fetched_bar_count += len(normalized)
                except Exception as error:
                    errors.append(f"{symbol}: 历史日线无效: {error}")
            if cache is not None:
                cache.upsert_many(normalized_batch)
                cache.mark_fetched_range(list(normalized_batch), range_start, range_end)
            else:
                bars.update(normalized_batch)

    if cache is not None:
        cache_started = time.perf_counter()
        bars = cache.load_bars(normalized_symbols, start_date, end_date)
        cache_read_seconds += time.perf_counter() - cache_started
    return bars, errors, CacheStats(
        cache_hit_symbols=cache_hit_symbols,
        fetched_symbols=len(fetch_symbols),
        cached_bar_count=cached_bar_count,
        fetched_bar_count=fetched_bar_count,
        fetch_requests=fetch_requests,
        cache_read_seconds=round(cache_read_seconds, 4),
        tq_fetch_seconds=round(tq_fetch_seconds, 4),
    )


def run_native_history(
    tq: Any,
    symbols: Sequence[str],
    formula_name: str,
    formula_arg: str,
    start_date: str,
    end_date: str,
    batch_size: int,
) -> Tuple[Dict[str, Set[str]], List[str]]:
    """Run the saved TDX formula over a historical date range in batches."""
    matches: Dict[str, Set[str]] = defaultdict(set)
    errors: List[str] = []
    for batch in chunks(list(symbols), batch_size):
        try:
            raw = tq.formula_process_mul_xg(
                formula_name=formula_name,
                formula_arg=formula_arg,
                return_count=0,
                return_date=True,
                stock_list=batch,
                stock_period="1d",
                start_time=_compact_date(start_date),
                end_time=_compact_date(end_date),
                count=0,
                dividend_type=0,
            )
            if isinstance(raw, dict) and raw.get("ErrorId") not in (None, 0, "0"):
                raise RuntimeError(raw.get("Error") or f"ErrorId={raw.get('ErrorId')}")
            for date, date_symbols in extract_historical_formula_matches(raw, batch).items():
                matches[date].update(date_symbols)
        except Exception as error:
            errors.append(f"{', '.join(batch)}: 原生历史公式失败: {error}")
    return {date: set(values) for date, values in sorted(matches.items())}, errors


def rank_concepts_by_date(
    board_bars: Dict[str, Sequence[DailyBar]],
    start_date: str,
    end_date: str,
    concept_limit: int,
) -> Dict[str, List[Tuple[str, float]]]:
    """Rank concept boards independently for each requested trading date."""
    changes: Dict[str, List[Tuple[str, float]]] = defaultdict(list)
    for symbol, bars in board_bars.items():
        for index in range(1, len(bars)):
            current = bars[index]
            previous = bars[index - 1]
            if start_date <= current.date <= end_date and previous.close > 0:
                change_pct = (current.close / previous.close - 1.0) * 100.0
                changes[current.date].append((symbol, change_pct))
    rankings: Dict[str, List[Tuple[str, float]]] = {}
    for date, rows in sorted(changes.items()):
        rows.sort(key=lambda item: (-item[1], item[0]))
        if len(rows) >= concept_limit:
            rankings[date] = rows[:concept_limit]
    return rankings


def _bars_through_date(
    bars: Sequence[DailyBar], date: str
) -> Tuple[List[DailyBar], Optional[int]]:
    for index, item in enumerate(bars):
        if item.date == date:
            return list(bars[: index + 1]), index
    return [], None


def _historical_upper_limit(symbol: str, previous_close: float) -> float:
    code = symbol.split(".", 1)[0]
    if code.startswith(("300", "301", "688")):
        ratio = 1.20
    elif code.startswith(("4", "8", "92")) or symbol.endswith(".BJ"):
        ratio = 1.30
    else:
        ratio = 1.10
    return math.floor(previous_close * ratio * 100.0 + 0.5) / 100.0


def score_replay_date(
    context: ReplayContext, date: str
) -> Tuple[str, List[Dict[str, Any]], int, List[str]]:
    """Score one historical date using only cached, non-TQ data."""
    ranked_concepts = context.concept_rankings[date]
    date_sources = context.sources.get(date, {})
    if not date_sources:
        return date, [], 0, []
    native_for_date = context.native_matches.get(date, set())
    python_for_date = context.python_matches.get(date, set())
    scored_rows: List[Dict[str, Any]] = []
    zero_score_count = 0
    errors: List[str] = []
    for board_rank, (sector, board_change_pct) in enumerate(ranked_concepts, start=1):
        sector_candidates = [
            symbol for symbol in date_sources if sector in context.sector_members.get(symbol, [])
        ]
        if not sector_candidates:
            continue
        sector_stock_bars: Dict[str, Sequence[DailyBar]] = {}
        for symbol in context.members_by_sector.get(sector, ()):
            index = context.date_indexes[symbol].get(date)
            if index is not None:
                sector_stock_bars[symbol] = HistoricalBarWindow(context.stock_bars[symbol], index)
        snapshots: Dict[str, MarketSnapshot] = {}
        for symbol in sector_candidates:
            bars = sector_stock_bars.get(symbol)
            if bars and len(bars) >= 2:
                snapshots[symbol] = MarketSnapshot(
                    last_price=bars[-1].close,
                    upper_limit=_historical_upper_limit(symbol, bars[-2].close),
                    stock_name=context.stock_names.get(symbol, ""),
                )
        signal_map = {
            symbol: context.python_signals[(date, symbol)]
            for symbol in sector_candidates
            if (date, symbol) in context.python_signals
        }
        try:
            candidates = apply_sector_rank_score_caps(
                rank_sector_candidates(
                    sector=sector,
                    board_rank=board_rank,
                    board_count=len(ranked_concepts),
                    candidate_symbols=sector_candidates,
                    stock_bars=sector_stock_bars,
                    signals=signal_map,
                    snapshots=snapshots,
                    board_change_pct=board_change_pct,
                    native_matches=native_for_date,
                    python_matches=python_for_date,
                    stock_names=context.stock_names,
                    formula_arg=context.formula_arg,
                )
            )
        except Exception as error:
            errors.append(f"{date} {sector}: 历史评分失败: {error}")
            continue
        for candidate in candidates:
            scored_rows.append(
                {
                    **asdict(candidate),
                    "date": date,
                    "board_rank": board_rank,
                    "best_sector": sector,
                    "best_sector_name": context.sector_names.get(sector, ""),
                    "sectors": [
                        code
                        for code, _ in ranked_concepts
                        if code in context.sector_members.get(candidate.symbol, [])
                    ],
                }
            )
            if candidate.final_score <= 0:
                zero_score_count += 1
    return date, scored_rows, zero_score_count, errors


_WORKER_CONTEXT: Optional[ReplayContext] = None


def _initialize_replay_worker(context: ReplayContext) -> None:
    global _WORKER_CONTEXT
    _WORKER_CONTEXT = context


def _score_replay_date_chunk(dates: Sequence[str]) -> List[Tuple[str, List[Dict[str, Any]], int, List[str]]]:
    if _WORKER_CONTEXT is None:
        raise RuntimeError("回测并行评分上下文未初始化")
    return [score_replay_date(_WORKER_CONTEXT, date) for date in dates]


def _split_dates(dates: Sequence[str], parts: int) -> List[List[str]]:
    if not dates:
        return []
    parts = max(1, min(parts, len(dates)))
    return [list(dates[index::parts]) for index in range(parts)]


def run_scoring_replay(
    context: ReplayContext, workers: int
) -> Tuple[List[Tuple[str, List[Dict[str, Any]], int, List[str]]], List[str], int]:
    """Run CPU-only daily scoring serially or in isolated worker processes."""
    dates = sorted(context.concept_rankings)
    if workers <= 1 or len(dates) <= 1:
        return [score_replay_date(context, date) for date in dates], [], 1
    try:
        chunks_by_worker = _split_dates(dates, workers)
        with ProcessPoolExecutor(
            max_workers=len(chunks_by_worker),
            initializer=_initialize_replay_worker,
            initargs=(context,),
        ) as executor:
            grouped = list(executor.map(_score_replay_date_chunk, chunks_by_worker))
        return (
            sorted((item for group in grouped for item in group), key=lambda item: item[0]),
            [],
            len(chunks_by_worker),
        )
    except Exception as error:
        fallback = [score_replay_date(context, date) for date in dates]
        return fallback, [f"并行评分失败，已回退单进程: {error}"], 1


def _score_bucket(score: float) -> str:
    if score < 50:
        return "<50"
    if score < 60:
        return "50-<60"
    if score < 70:
        return "60-<70"
    if score < 80:
        return "70-<80"
    if score < 90:
        return "80-<90"
    return "90-100"


def _summaries_by_group(
    picks: Sequence[Dict[str, Any]], key: str, values: Sequence[str]
) -> Dict[str, Dict[str, Any]]:
    return {
        value: summarize_cohort([row for row in picks if row.get(key) == value], 5)
        for value in values
    }


def _validate_date(value: str, flag: str) -> datetime:
    try:
        return datetime.strptime(value, "%Y-%m-%d")
    except ValueError as error:
        raise ValueError(f"{flag} 必须是 YYYY-MM-DD: {value}") from error


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="通达信砖型图历史评分有效性回测")
    parser.add_argument("--start-date", required=True, help="回测开始日期 YYYY-MM-DD")
    parser.add_argument("--end-date", required=True, help="回测结束日期 YYYY-MM-DD")
    parser.add_argument(
        "--tdx-path",
        default=os.environ.get("TDX_PATH", r"F:\new_tdx64"),
        help="通达信安装目录",
    )
    parser.add_argument("--formula-name", default="ZHUAN", help="通达信历史选股公式名称")
    parser.add_argument("--formula-arg", default="14,28,57,114,3,21", help="公式参数")
    parser.add_argument("--concept-limit", type=int, default=5, help="每日概念板块数量")
    parser.add_argument("--batch-size", type=int, default=200, help="TQ 批量证券数量")
    parser.add_argument(
        "--workers",
        type=int,
        default=1,
        help="纯 Python 历史评分进程数；TQ 取数和原生公式始终单线程",
    )
    parser.add_argument(
        "--cache-path",
        type=Path,
        default=DEFAULT_BACKTEST_CACHE_DB,
        help="未复权历史日线 SQLite 缓存路径",
    )
    parser.add_argument(
        "--refresh-cache",
        action="store_true",
        help="忽略本次区间的日线缓存并重新从通达信拉取",
    )
    parser.add_argument("--output-dir", help="回测报告目录；默认按日期区间生成")
    return parser.parse_args(argv)


def build_backtest_payload(
    args: argparse.Namespace, tq: Any, cache: Optional[BacktestBarCache] = None
) -> Dict[str, Any]:
    """Replay both engines over a date range and score each day's global Top 5."""
    started = time.perf_counter()
    stages: Dict[str, float] = {}
    start = _validate_date(args.start_date, "--start-date")
    end = _validate_date(args.end_date, "--end-date")
    if start > end:
        raise ValueError("--start-date 不得晚于 --end-date")
    if args.concept_limit <= 0 or args.batch_size <= 0 or args.workers <= 0:
        raise ValueError("概念板块数量、批次大小和并行进程数必须大于 0")

    warmup_start = (start - timedelta(days=400)).strftime("%Y-%m-%d")
    query_end = (end + timedelta(days=30)).strftime("%Y-%m-%d")
    stage_started = time.perf_counter()
    concept_codes = fetch_concept_boards(tq, args.tdx_path)
    sector_names = fetch_sector_names(tq)
    board_bars, board_errors, concept_cache_stats = fetch_historical_bars(
        tq,
        concept_codes,
        warmup_start,
        args.end_date,
        args.batch_size,
        cache=cache,
        refresh_cache=args.refresh_cache,
    )
    concept_rankings = rank_concepts_by_date(
        board_bars, args.start_date, args.end_date, args.concept_limit
    )
    stages["concepts"] = round(time.perf_counter() - stage_started, 4)
    if not concept_rankings:
        raise RuntimeError("指定区间没有足够的概念板块历史日线")

    stage_started = time.perf_counter()
    selected_sectors = sorted(
        {sector for ranking in concept_rankings.values() for sector, _ in ranking}
    )
    sector_members = fetch_sector_members(tq, selected_sectors)
    symbols = sorted(sector_members)
    stock_bars, stock_errors, stock_cache_stats = fetch_historical_bars(
        tq,
        symbols,
        warmup_start,
        query_end,
        args.batch_size,
        cache=cache,
        refresh_cache=args.refresh_cache,
    )
    stages["stock_bars"] = round(time.perf_counter() - stage_started, 4)
    stage_started = time.perf_counter()
    native_matches, native_errors = run_native_history(
        tq,
        symbols,
        args.formula_name,
        args.formula_arg,
        warmup_start,
        args.end_date,
        args.batch_size,
    )
    stages["native_formula"] = round(time.perf_counter() - stage_started, 4)
    stage_started = time.perf_counter()
    python_matches, python_signals = historical_python_signals(stock_bars)
    sources = signal_sources_by_date(native_matches, python_matches)
    signal_symbols = sorted(
        {
            symbol
            for date, date_sources in sources.items()
            if args.start_date <= date <= args.end_date
            for symbol in date_sources
        }
    )
    stock_names = fetch_stock_names(tq, signal_symbols, {})

    context = build_replay_context(
        concept_rankings=concept_rankings,
        sector_members=sector_members,
        stock_bars=stock_bars,
        native_matches=native_matches,
        python_matches=python_matches,
        python_signals=python_signals,
        sources=sources,
        stock_names=stock_names,
        sector_names=sector_names,
        formula_arg=args.formula_arg,
    )
    replay_results, replay_errors, workers_used = run_scoring_replay(context, args.workers)
    stages["scoring"] = round(time.perf_counter() - stage_started, 4)
    picks: List[Dict[str, Any]] = []
    consensus_picks: List[Dict[str, Any]] = []
    zero_score_count = 0
    for date, scored_rows, date_zero_score_count, date_errors in replay_results:
        zero_score_count += date_zero_score_count
        replay_errors.extend(date_errors)
        selections = (
            (picks, select_top_positive(scored_rows, limit=5)),
            (consensus_picks, select_consensus_picks(scored_rows, limit=5)),
        )
        for destination, selected in selections:
            for row in selected:
                bars = stock_bars.get(row["symbol"], [])
                signal_index = context.date_indexes.get(row["symbol"], {}).get(date, -1)
                outcomes = forward_outcomes(bars, signal_index)
                destination.append(
                    {
                        **row,
                        **outcomes,
                        "score_bucket": _score_bucket(float(row["final_score"])),
                    }
                )

    picks.sort(key=lambda row: (row["date"], int(row["rank"])))
    consensus_picks.sort(key=lambda row: (row["date"], int(row["rank"])))
    errors = board_errors + stock_errors + native_errors + replay_errors
    source_values = ("shared", "native_only", "python_only")
    bucket_values = ("<50", "50-<60", "60-<70", "70-<80", "80-<90", "90-100")
    return {
        "ok": True,
        "status": "partial" if errors else "complete",
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "start_date": args.start_date,
        "end_date": args.end_date,
        "warmup_start": warmup_start,
        "formula_name": args.formula_name,
        "formula_arg": args.formula_arg,
        "execution_policy": "仅双引擎共振的正分 Top 3 可作为执行候选；单端信号仅观察。",
        "factor_weights": dict(FACTOR_WEIGHTS),
        "concept_limit": args.concept_limit,
        "top_limit": 5,
        "workers": workers_used,
        "requested_workers": args.workers,
        "stages_seconds": {**stages, "total": round(time.perf_counter() - started, 4)},
        "cache": {
            "path": str(args.cache_path) if cache is not None else None,
            "refresh_requested": bool(args.refresh_cache),
            "concepts": asdict(concept_cache_stats),
            "stock_bars": asdict(stock_cache_stats),
        },
        "valid_trading_days": len(concept_rankings),
        "signal_days": len({row["date"] for row in picks}),
        "pick_count": len(picks),
        "consensus_signal_days": len({row["date"] for row in consensus_picks}),
        "consensus_pick_count": len(consensus_picks),
        "zero_score_count": zero_score_count,
        "picks": picks,
        "cohorts": {
            "top_1": summarize_cohort(picks, 1),
            "top_3": summarize_cohort(picks, 3),
            "top_5": summarize_cohort(picks, 5),
        },
        "consensus_picks": consensus_picks,
        "consensus_cohorts": {
            "top_1": summarize_cohort(consensus_picks, 1),
            "top_3": summarize_cohort(consensus_picks, 3),
            "top_5": summarize_cohort(consensus_picks, 5),
        },
        "source_summaries": _summaries_by_group(picks, "signal_source", source_values),
        "score_summaries": _summaries_by_group(picks, "score_bucket", bucket_values),
        "errors": errors,
        "limitations": [
            "概念板块成分使用回测运行时的当前板块成分，存在前视偏差。",
            "信号日收盘价近似尾盘成交价，不包含滑点、手续费和冲击成本。",
            "日线只能分别判断次日是否触及 +3% 与 -3%，不能确定盘中先后顺序。",
            "历史 ST、上市初期及特殊涨跌停状态可能无法完整重建。",
        ],
    }


def _format_percent(value: Any) -> str:
    number = _finite_number(value)
    return "-" if number is None else f"{number * 100:.2f}%"


def _cohort_row(label: str, summary: Dict[str, Any]) -> str:
    return "| {label} | {days} | {picks} | {r1} | {r2} | {r3} | {r5} | {plus} | {minus} |".format(
        label=label,
        days=summary.get("signal_days", 0),
        picks=summary.get("picks", 0),
        r1=_format_percent(summary.get("return_1d", {}).get("mean")),
        r2=_format_percent(summary.get("return_2d", {}).get("mean")),
        r3=_format_percent(summary.get("return_3d", {}).get("mean")),
        r5=_format_percent(summary.get("return_5d", {}).get("mean")),
        plus=_format_percent(summary.get("hit_plus_3pct_next_day")),
        minus=_format_percent(summary.get("hit_minus_3pct_next_day")),
    )


def render_backtest_markdown(payload: Dict[str, Any]) -> str:
    """Render a human-readable Top 1/3/5 scoring-validation report."""
    cohorts = payload["cohorts"]
    lines = [
        "# 通达信砖型图评分回测",
        "",
        f"- 回测区间：{payload['start_date']} 至 {payload['end_date']}",
        f"- 状态：{payload['status']}",
        f"- 有效交易日：{payload['valid_trading_days']}",
        f"- 有候选交易日：{payload['signal_days']}",
        f"- Top 5 样本数：{payload['pick_count']}",
        f"- 纯评分进程数：{payload.get('workers', 1)}/{payload.get('requested_workers', 1)}（实际/请求；TQ 始终单线程）",
        f"- 公式：{payload['formula_name']}（{payload['formula_arg']}）",
    ]
    execution_policy = payload.get("execution_policy")
    if execution_policy:
        lines.append(f"- 执行规则：{execution_policy}")
    factor_weights = payload.get("factor_weights")
    if factor_weights:
        lines.append(
            "- 评分权重："
            + "、".join(
                f"{name}={float(weight):g}"
                for name, weight in sorted(factor_weights.items())
            )
        )
    cache = payload.get("cache")
    if cache and cache.get("path"):
        concept_cache = cache.get("concepts", {})
        stock_cache = cache.get("stock_bars", {})
        lines.extend(
            [
                f"- 日线缓存：{cache['path']}",
                f"- 缓存刷新：{'是' if cache.get('refresh_requested') else '否'}",
                "",
                "## 日线缓存",
                "",
                "| 范围 | 完整缓存股票 | 本次请求股票 | 缓存K线 | 新拉取K线 | TQ请求次数 | 缓存读取 | TQ耗时 |",
                "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
                "| 概念板块 | {hit} | {fetched} | {cached} | {new} | {requests} | {read:.2f} 秒 | {tq:.2f} 秒 |".format(
                    hit=concept_cache.get("cache_hit_symbols", 0),
                    fetched=concept_cache.get("fetched_symbols", 0),
                    cached=concept_cache.get("cached_bar_count", 0),
                    new=concept_cache.get("fetched_bar_count", 0),
                    requests=concept_cache.get("fetch_requests", 0),
                    read=float(concept_cache.get("cache_read_seconds", 0.0)),
                    tq=float(concept_cache.get("tq_fetch_seconds", 0.0)),
                ),
                "| 成分股 | {hit} | {fetched} | {cached} | {new} | {requests} | {read:.2f} 秒 | {tq:.2f} 秒 |".format(
                    hit=stock_cache.get("cache_hit_symbols", 0),
                    fetched=stock_cache.get("fetched_symbols", 0),
                    cached=stock_cache.get("cached_bar_count", 0),
                    new=stock_cache.get("fetched_bar_count", 0),
                    requests=stock_cache.get("fetch_requests", 0),
                    read=float(stock_cache.get("cache_read_seconds", 0.0)),
                    tq=float(stock_cache.get("tq_fetch_seconds", 0.0)),
                ),
            ]
        )
    lines.extend(
        [
            "",
            "## Top 1 / Top 3 / Top 5",
            "",
            "| 组合 | 信号日 | 股票样本 | 1日均值 | 2日均值 | 3日均值 | 5日均值 | 次日触及+3% | 次日触及-3% |",
            "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
            _cohort_row("Top 3（核心）", cohorts["top_3"]),
            _cohort_row("Top 1", cohorts["top_1"]),
            _cohort_row("Top 5", cohorts["top_5"]),
            "",
            "## 信号来源",
            "",
            "| 来源 | 信号日 | 股票样本 | 1日均值 | 2日均值 | 3日均值 | 5日均值 | 次日触及+3% | 次日触及-3% |",
            "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
        ]
    )
    source_labels = {"shared": "双引擎", "native_only": "仅原生", "python_only": "仅Python"}
    for key in ("shared", "native_only", "python_only"):
        lines.append(_cohort_row(source_labels[key], payload["source_summaries"][key]))
    consensus_cohorts = payload.get("consensus_cohorts")
    if consensus_cohorts:
        lines.extend(
            [
                "",
                "## 双引擎共振组合（独立排序）",
                "",
                "| 组合 | 信号日 | 股票样本 | 1日均值 | 2日均值 | 3日均值 | 5日均值 | 次日触及+3% | 次日触及-3% |",
                "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
                _cohort_row("共振 Top 3（核心）", consensus_cohorts["top_3"]),
                _cohort_row("共振 Top 1", consensus_cohorts["top_1"]),
                _cohort_row("共振 Top 5", consensus_cohorts["top_5"]),
            ]
        )
    lines.extend(
        [
            "",
            "## 分数区间",
            "",
            "| 分数 | 信号日 | 股票样本 | 1日均值 | 2日均值 | 3日均值 | 5日均值 | 次日触及+3% | 次日触及-3% |",
            "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
        ]
    )
    for key, summary in payload["score_summaries"].items():
        lines.append(_cohort_row(key, summary))
    lines.extend(
        [
            "",
            "## 每日 Top 5",
            "",
            "| 日期 | 排名 | 代码 | 名称 | 来源 | 最高评分板块 | 总分 | 1日 | 2日 | 3日 | 5日 |",
            "| --- | ---: | --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |",
        ]
    )
    source_labels = {"shared": "双引擎", "native_only": "仅原生", "python_only": "仅Python"}
    for row in payload["picks"]:
        sector = row.get("best_sector_name") or row.get("best_sector") or "-"
        lines.append(
            f"| {row['date']} | {row['rank']} | {row['symbol']} | {row.get('stock_name') or '未知'} | "
            f"{source_labels.get(row.get('signal_source'), row.get('signal_source', '未知'))} | {sector} | "
            f"{row['final_score']:.2f} | {_format_percent(row.get('return_1d'))} | "
            f"{_format_percent(row.get('return_2d'))} | {_format_percent(row.get('return_3d'))} | "
            f"{_format_percent(row.get('return_5d'))} |"
        )
    if not payload["picks"]:
        lines.append("| - | - | - | - | - | - | - | - | - | - | - |")
    lines.extend(["", "## 数据限制", ""])
    lines.extend(f"- {item}" for item in payload["limitations"])
    if payload.get("errors"):
        lines.extend(["", "## 数据错误", ""])
        lines.extend(f"- {error}" for error in payload["errors"][:100])
        if len(payload["errors"]) > 100:
            lines.append(f"- 其余 {len(payload['errors']) - 100} 条错误见运行日志。")
    if payload.get("stages_seconds"):
        lines.extend(["", "## 阶段耗时", ""])
        lines.extend(
            f"- {name}: {seconds:.2f} 秒"
            for name, seconds in payload["stages_seconds"].items()
        )
    return "\n".join(lines) + "\n"


CSV_COLUMNS = [
    "date", "rank", "symbol", "stock_name", "signal_source", "best_sector",
    "best_sector_name", "sectors", "final_score", "base_score", "risk_penalty",
    "board_leadership", "relative_strength", "liquidity", "brick_strength",
    "white_yellow_trend", "previous_kdj_j", "previous_kdj_doji", "score_bucket",
    "return_1d", "return_2d", "return_3d", "return_5d",
    "next_day_high_return", "next_day_low_return",
    "hit_plus_3pct_next_day", "hit_minus_3pct_next_day",
]


def _csv_row(row: Dict[str, Any]) -> Dict[str, Any]:
    factors = row.get("factor_scores", {})
    result: Dict[str, Any] = {}
    for column in CSV_COLUMNS:
        if column in factors:
            value = factors[column]
        elif column == "sectors":
            value = ";".join(row.get("sectors", []))
        else:
            value = row.get(column, "")
        if isinstance(value, float) and not math.isfinite(value):
            value = ""
        if value is None:
            value = ""
        result[column] = value
    return result


def default_output_dir(start_date: str, end_date: str) -> Path:
    root = Path(__file__).resolve().parents[2]
    return root / "data" / "tdx-brick-selector" / "backtests" / f"{_compact_date(start_date)}_{_compact_date(end_date)}"


def write_backtest_outputs(
    payload: Dict[str, Any], output_dir: Path
) -> Tuple[Path, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    report_path = output_dir / "report.md"
    csv_path = output_dir / "picks.csv"
    consensus_csv_path = output_dir / "consensus-picks.csv"
    report_path.write_text(render_backtest_markdown(payload), encoding="utf-8")
    with csv_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_COLUMNS)
        writer.writeheader()
        writer.writerows(_csv_row(row) for row in payload["picks"])
    with consensus_csv_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_COLUMNS)
        writer.writeheader()
        writer.writerows(_csv_row(row) for row in payload.get("consensus_picks", []))
    return report_path, csv_path


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    tq = None
    cache = None
    try:
        cache = BacktestBarCache(args.cache_path)
        tq = load_tq(args.tdx_path)
        payload = build_backtest_payload(args, tq, cache)
        output_dir = Path(args.output_dir) if args.output_dir else default_output_dir(
            args.start_date, args.end_date
        )
        report_path, csv_path = write_backtest_outputs(payload, output_dir)
        print(render_backtest_markdown(payload), end="")
        print(f"报告已写入: {report_path}")
        print(f"明细已写入: {csv_path}")
        print(f"双引擎共振明细已写入: {output_dir / 'consensus-picks.csv'}")
        return 0
    except Exception as error:
        print(f"通达信砖型图回测失败: {error}", file=sys.stderr)
        return 1
    finally:
        if tq is not None and hasattr(tq, "close"):
            tq.close()
        if cache is not None:
            cache.close()


if __name__ == "__main__":
    raise SystemExit(main())
