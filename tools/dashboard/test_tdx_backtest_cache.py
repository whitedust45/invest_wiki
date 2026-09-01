#!/usr/bin/env python3
"""Offline tests for the persistent TDX backtest daily-bar cache."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.short_term.backtest_cache import (  # noqa: E402
    BacktestBarCache,
    dynamic_batch_size,
)
from modules.short_term import backtest_brick as backtest  # noqa: E402
from modules.short_term.strategies.brick import DailyBar  # noqa: E402


def bar(date: str, close: float) -> DailyBar:
    return DailyBar(
        date=date,
        open=close - 0.1,
        high=close + 0.2,
        low=close - 0.3,
        close=close,
        volume=123.0,
        amount=456.0,
    )


class BacktestBarCacheTests(unittest.TestCase):
    def test_cache_round_trip_and_schema_has_no_foreign_keys(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            cache = BacktestBarCache(Path(directory) / "cache.db")
            try:
                cache.upsert_bars("000001.SZ", [bar("2026-07-01", 10.0), bar("2026-07-02", 10.5)])
                loaded = cache.load_bars(["000001.SZ"], "2026-07-01", "2026-07-02")
                ddl = cache.connection.execute(
                    "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'tdx_backtest_daily_bars'"
                ).fetchone()[0]
            finally:
                cache.close()

        self.assertEqual(loaded["000001.SZ"], [bar("2026-07-01", 10.0), bar("2026-07-02", 10.5)])
        self.assertNotIn("FOREIGN KEY", ddl.upper())

    def test_missing_ranges_only_request_uncovered_prefix_or_suffix(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            cache = BacktestBarCache(Path(directory) / "cache.db")
            try:
                self.assertEqual(
                    cache.missing_ranges(["000001.SZ"], "2026-07-01", "2026-07-05"),
                    {("2026-07-01", "2026-07-05"): ["000001.SZ"]},
                )
                cache.upsert_bars(
                    "000001.SZ",
                    [bar("2026-07-02", 10.0), bar("2026-07-03", 10.2), bar("2026-07-04", 10.4)],
                )
                self.assertEqual(
                    cache.missing_ranges(["000001.SZ"], "2026-07-01", "2026-07-05"),
                    {
                        ("2026-07-01", "2026-07-01"): ["000001.SZ"],
                        ("2026-07-05", "2026-07-05"): ["000001.SZ"],
                    },
                )
                cache.upsert_bars("000001.SZ", [bar("2026-07-01", 9.9), bar("2026-07-05", 10.6)])
                self.assertEqual(
                    cache.missing_ranges(["000001.SZ"], "2026-07-01", "2026-07-05"),
                    {},
                )
            finally:
                cache.close()

    def test_refresh_marks_the_entire_requested_range_missing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            cache = BacktestBarCache(Path(directory) / "cache.db")
            try:
                cache.upsert_bars("000001.SZ", [bar("2026-07-01", 10.0), bar("2026-07-02", 10.2)])
                self.assertEqual(
                    cache.missing_ranges(
                        ["000001.SZ"], "2026-07-01", "2026-07-02", refresh=True
                    ),
                    {("2026-07-01", "2026-07-02"): ["000001.SZ"]},
                )
            finally:
                cache.close()

    def test_dynamic_batch_size_respects_the_tq_record_limit(self) -> None:
        batch_size = dynamic_batch_size("2025-01-01", "2026-07-31", requested_batch_size=200)

        self.assertLessEqual(batch_size * 413, 24_000)
        self.assertGreater(batch_size, 0)

    def test_historical_fetch_uses_cache_on_the_second_identical_range(self) -> None:
        class FakeTq:
            def __init__(self) -> None:
                self.calls = 0

            def get_market_data(self, **_: object) -> object:
                self.calls += 1
                return {"raw": "frames"}

        with tempfile.TemporaryDirectory() as directory:
            cache = BacktestBarCache(Path(directory) / "cache.db")
            tq = FakeTq()
            try:
                with patch.object(backtest, "_find_field_frames", return_value={}), patch.object(
                    backtest, "_normalize_symbol_bars", return_value=[bar("2026-07-01", 10.0)]
                ):
                    first_bars, first_errors, first_stats = backtest.fetch_historical_bars(
                        tq,
                        ["000001.SZ"],
                        "2026-07-01",
                        "2026-07-02",
                        batch_size=200,
                        cache=cache,
                    )
                    second_bars, second_errors, second_stats = backtest.fetch_historical_bars(
                        tq,
                        ["000001.SZ"],
                        "2026-07-01",
                        "2026-07-02",
                        batch_size=200,
                        cache=cache,
                    )
            finally:
                cache.close()

        self.assertEqual(first_errors, [])
        self.assertEqual(second_errors, [])
        self.assertEqual(first_bars, second_bars)
        self.assertEqual(tq.calls, 1)
        self.assertEqual(first_stats.fetched_symbols, 1)
        self.assertEqual(second_stats.cache_hit_symbols, 1)
        self.assertEqual(second_stats.fetch_requests, 0)


if __name__ == "__main__":
    unittest.main()
