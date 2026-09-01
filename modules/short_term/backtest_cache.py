"""Persistent unadjusted TDX daily-bar cache for brick backtests.

The cache is deliberately separate from the dashboard market-data store: it
contains only the raw, unadjusted bars used by the TDX strategy replay.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta
import math
from pathlib import Path
import sqlite3
from typing import Dict, Iterable, List, Sequence, Tuple

from modules.short_term.strategies.brick import DailyBar


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_BACKTEST_CACHE_DB = ROOT / "data" / "tdx-brick-selector" / "backtest-cache.db"
_MAX_TQ_RECORDS_PER_QUERY = 24_000
_SQLITE_VARIABLE_BATCH = 900


@dataclass(frozen=True)
class CacheStats:
    cache_hit_symbols: int = 0
    fetched_symbols: int = 0
    cached_bar_count: int = 0
    fetched_bar_count: int = 0
    fetch_requests: int = 0
    cache_read_seconds: float = 0.0
    tq_fetch_seconds: float = 0.0


def _normalize_symbols(symbols: Iterable[str]) -> List[str]:
    return sorted({str(symbol).strip().upper() for symbol in symbols if str(symbol).strip()})


def _as_date(value: str) -> datetime:
    return datetime.strptime(value, "%Y-%m-%d")


def _date_text(value: datetime) -> str:
    return value.strftime("%Y-%m-%d")


def _previous_day(value: str) -> str:
    return _date_text(_as_date(value) - timedelta(days=1))


def _next_day(value: str) -> str:
    return _date_text(_as_date(value) + timedelta(days=1))


def dynamic_batch_size(start_date: str, end_date: str, requested_batch_size: int) -> int:
    """Keep each TQ daily-bar request under the documented record limit."""
    if requested_batch_size <= 0:
        raise ValueError("TQ 批量证券数量必须大于 0")
    calendar_days = (_as_date(end_date) - _as_date(start_date)).days + 1
    if calendar_days <= 0:
        raise ValueError("历史日线结束日期不得早于开始日期")
    estimated_trading_days = max(1, math.ceil(calendar_days * 5 / 7))
    safe_symbol_count = max(1, _MAX_TQ_RECORDS_PER_QUERY // estimated_trading_days)
    return min(requested_batch_size, safe_symbol_count)


class BacktestBarCache:
    """SQLite cache using redundant symbol keys and no foreign keys."""

    def __init__(self, path: Path | str = DEFAULT_BACKTEST_CACHE_DB) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(self.path)
        self._initialize_schema()

    def close(self) -> None:
        self.connection.close()

    def _initialize_schema(self) -> None:
        self.connection.executescript(
            """
            CREATE TABLE IF NOT EXISTS tdx_backtest_daily_bars (
              symbol TEXT NOT NULL,
              trade_date TEXT NOT NULL,
              open REAL NOT NULL,
              high REAL NOT NULL,
              low REAL NOT NULL,
              close REAL NOT NULL,
              volume REAL NOT NULL,
              amount REAL NOT NULL,
              source TEXT NOT NULL DEFAULT 'tdx_unadjusted',
              updated_at TEXT NOT NULL,
              PRIMARY KEY (symbol, trade_date)
            );

            CREATE INDEX IF NOT EXISTS idx_tdx_backtest_daily_bars_symbol_date
            ON tdx_backtest_daily_bars(symbol, trade_date);

            CREATE TABLE IF NOT EXISTS tdx_backtest_fetch_ranges (
              symbol TEXT NOT NULL,
              start_date TEXT NOT NULL,
              end_date TEXT NOT NULL,
              source TEXT NOT NULL DEFAULT 'tdx_unadjusted',
              updated_at TEXT NOT NULL,
              PRIMARY KEY (symbol, start_date, end_date)
            );

            CREATE INDEX IF NOT EXISTS idx_tdx_backtest_fetch_ranges_symbol_date
            ON tdx_backtest_fetch_ranges(symbol, start_date, end_date);
            """
        )
        self.connection.commit()

    def upsert_bars(self, symbol: str, bars: Sequence[DailyBar]) -> None:
        self.upsert_many({symbol: bars})

    def upsert_many(self, bars_by_symbol: Dict[str, Sequence[DailyBar]]) -> None:
        rows = []
        updated_at = datetime.now().isoformat(timespec="seconds")
        for symbol, bars in bars_by_symbol.items():
            normalized_symbol = str(symbol).strip().upper()
            if not normalized_symbol:
                continue
            rows.extend(
                (
                    normalized_symbol,
                    item.date,
                    item.open,
                    item.high,
                    item.low,
                    item.close,
                    item.volume,
                    item.amount,
                    updated_at,
                )
                for item in bars
            )
        if not rows:
            return
        self.connection.executemany(
            """
            INSERT INTO tdx_backtest_daily_bars (
              symbol, trade_date, open, high, low, close, volume, amount, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(symbol, trade_date) DO UPDATE SET
              open = excluded.open,
              high = excluded.high,
              low = excluded.low,
              close = excluded.close,
              volume = excluded.volume,
              amount = excluded.amount,
              source = excluded.source,
              updated_at = excluded.updated_at
            """,
            rows,
        )
        self.connection.commit()

    def mark_fetched_range(
        self, symbols: Sequence[str], start_date: str, end_date: str
    ) -> None:
        normalized_symbols = _normalize_symbols(symbols)
        if not normalized_symbols:
            return
        updated_at = datetime.now().isoformat(timespec="seconds")
        self.connection.executemany(
            """
            INSERT INTO tdx_backtest_fetch_ranges (symbol, start_date, end_date, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(symbol, start_date, end_date) DO UPDATE SET
              source = excluded.source,
              updated_at = excluded.updated_at
            """,
            [(symbol, start_date, end_date, updated_at) for symbol in normalized_symbols],
        )
        self.connection.commit()

    def load_bars(
        self, symbols: Sequence[str], start_date: str, end_date: str
    ) -> Dict[str, List[DailyBar]]:
        result: Dict[str, List[DailyBar]] = defaultdict(list)
        normalized_symbols = _normalize_symbols(symbols)
        for offset in range(0, len(normalized_symbols), _SQLITE_VARIABLE_BATCH):
            batch = normalized_symbols[offset : offset + _SQLITE_VARIABLE_BATCH]
            placeholders = ", ".join("?" for _ in batch)
            rows = self.connection.execute(
                f"""
                SELECT symbol, trade_date, open, high, low, close, volume, amount
                FROM tdx_backtest_daily_bars
                WHERE symbol IN ({placeholders}) AND trade_date >= ? AND trade_date <= ?
                ORDER BY symbol, trade_date
                """,
                [*batch, start_date, end_date],
            ).fetchall()
            for symbol, date, open_, high, low, close, volume, amount in rows:
                result[symbol].append(
                    DailyBar(
                        date=date,
                        open=float(open_),
                        high=float(high),
                        low=float(low),
                        close=float(close),
                        volume=float(volume),
                        amount=float(amount),
                    )
                )
        return {symbol: bars for symbol, bars in result.items()}

    def missing_ranges(
        self,
        symbols: Sequence[str],
        start_date: str,
        end_date: str,
        *,
        refresh: bool = False,
    ) -> Dict[Tuple[str, str], List[str]]:
        """Return uncovered ranges grouped by identical date windows."""
        if _as_date(start_date) > _as_date(end_date):
            raise ValueError("历史日线结束日期不得早于开始日期")
        result: Dict[Tuple[str, str], List[str]] = defaultdict(list)
        for symbol in _normalize_symbols(symbols):
            if refresh:
                result[(start_date, end_date)].append(symbol)
                continue
            for missing_start, missing_end in self._uncovered_ranges(symbol, start_date, end_date):
                result[(missing_start, missing_end)].append(symbol)
        return {key: value for key, value in sorted(result.items())}

    def _uncovered_ranges(
        self, symbol: str, start_date: str, end_date: str
    ) -> List[Tuple[str, str]]:
        coverage = self.connection.execute(
            """
            SELECT start_date, end_date
            FROM tdx_backtest_fetch_ranges
            WHERE symbol = ? AND end_date >= ? AND start_date <= ?
            ORDER BY start_date, end_date
            """,
            (symbol, start_date, end_date),
        ).fetchall()
        if not coverage:
            row = self.connection.execute(
                """
                SELECT MIN(trade_date), MAX(trade_date)
                FROM tdx_backtest_daily_bars
                WHERE symbol = ? AND trade_date >= ? AND trade_date <= ?
                """,
                (symbol, start_date, end_date),
            ).fetchone()
            if row and row[0] and row[1]:
                coverage = [(row[0], row[1])]

        cursor = start_date
        missing: List[Tuple[str, str]] = []
        for covered_start, covered_end in coverage:
            if covered_end < cursor:
                continue
            if covered_start > cursor:
                missing.append((cursor, _previous_day(covered_start)))
            if covered_end >= end_date:
                return missing
            cursor = max(cursor, _next_day(covered_end))
        if cursor <= end_date:
            missing.append((cursor, end_date))
        return missing
