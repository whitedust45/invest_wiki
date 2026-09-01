#!/usr/bin/env python3
"""Offline tests for the TDX brick scoring backtest."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path
from typing import Optional
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.short_term import backtest_brick as backtest  # noqa: E402
from modules.short_term.strategies.brick import (  # noqa: E402
    BrickSignal,
    CandidateScore,
    DailyBar,
)


def bar(date: str, close: float, high: Optional[float] = None, low: Optional[float] = None) -> DailyBar:
    return DailyBar(
        date=date,
        open=close,
        high=high if high is not None else close,
        low=low if low is not None else close,
        close=close,
        volume=100.0,
        amount=1000.0,
    )


class BrickBacktestTests(unittest.TestCase):
    def test_extract_historical_formula_matches_reads_dated_xg_values(self) -> None:
        raw = {
            "000001.SZ": {
                "XG": [
                    {"Date": "20260701", "Value": "0"},
                    {"Date": "20260702", "Value": "1"},
                ]
            },
            "600000.SH": {"XG": [{"Date": "2026-07-01", "Value": 1}]},
            "300001.SZ": {"NOT_XG": [{"Date": "20260701", "Value": 1}]},
            "ErrorId": "0",
        }

        matches = backtest.extract_historical_formula_matches(
            raw, ["000001.SZ", "600000.SH", "300001.SZ"]
        )

        self.assertEqual(
            matches,
            {
                "2026-07-01": {"600000.SH"},
                "2026-07-02": {"000001.SZ"},
            },
        )

    def test_historical_python_signals_map_brick_index_to_bar_date(self) -> None:
        bars = {
            "000001.SZ": [bar(f"2026-07-0{index}", 10.0 + index) for index in range(1, 7)]
        }
        expected_signal = BrickSignal(2.0, 1.0, 2.0, 1.0, 1.0)

        with patch.object(backtest, "calculate_brick_series", return_value=[1.0, 2.0, 2.0, 1.0, 2.0]), \
             patch.object(backtest, "strict_brick_signal", return_value=expected_signal):
            matches, signals = backtest.historical_python_signals(bars)

        self.assertEqual(matches, {"2026-07-06": {"000001.SZ"}})
        self.assertEqual(signals[("2026-07-06", "000001.SZ")], expected_signal)

    def test_signal_sources_merge_native_and_python_by_date(self) -> None:
        sources = backtest.signal_sources_by_date(
            {"2026-07-01": {"000001.SZ", "600000.SH"}},
            {"2026-07-01": {"000001.SZ", "300001.SZ"}},
        )

        self.assertEqual(
            sources["2026-07-01"],
            {
                "000001.SZ": "shared",
                "300001.SZ": "python_only",
                "600000.SH": "native_only",
            },
        )

    def test_select_top_positive_deduplicates_and_excludes_zero_scores(self) -> None:
        rows = [
            {"symbol": "000001.SZ", "final_score": 80.0, "board_rank": 2},
            {"symbol": "000001.SZ", "final_score": 85.0, "board_rank": 1},
            {"symbol": "000002.SZ", "final_score": 70.0, "board_rank": 1},
            {"symbol": "000003.SZ", "final_score": 60.0, "board_rank": 1},
            {"symbol": "000004.SZ", "final_score": 50.0, "board_rank": 1},
            {"symbol": "000005.SZ", "final_score": 40.0, "board_rank": 1},
            {"symbol": "000006.SZ", "final_score": 30.0, "board_rank": 1},
            {"symbol": "000007.SZ", "final_score": 0.0, "board_rank": 1},
        ]

        selected = backtest.select_top_positive(rows, limit=5)

        self.assertEqual(
            [row["symbol"] for row in selected],
            ["000001.SZ", "000002.SZ", "000003.SZ", "000004.SZ", "000005.SZ"],
        )
        self.assertEqual([row["rank"] for row in selected], [1, 2, 3, 4, 5])

    def test_consensus_selection_reranks_only_shared_candidates(self) -> None:
        rows = [
            {"symbol": "000001.SZ", "signal_source": "native_only", "final_score": 90.0},
            {"symbol": "000002.SZ", "signal_source": "shared", "final_score": 80.0},
            {"symbol": "000003.SZ", "signal_source": "shared", "final_score": 70.0},
            {"symbol": "000004.SZ", "signal_source": "python_only", "final_score": 60.0},
        ]

        selected = backtest.select_consensus_picks(rows, limit=5)

        self.assertEqual([row["symbol"] for row in selected], ["000002.SZ", "000003.SZ"])
        self.assertEqual([row["rank"] for row in selected], [1, 2])

    def test_forward_outcomes_use_signal_close_and_preserve_missing_horizons(self) -> None:
        bars = [
            bar("2026-07-01", 10.0),
            bar("2026-07-02", 10.2, high=10.4, low=9.6),
            bar("2026-07-03", 11.0),
        ]

        outcomes = backtest.forward_outcomes(bars, signal_index=0)

        self.assertAlmostEqual(outcomes["return_1d"], 0.02)
        self.assertAlmostEqual(outcomes["return_2d"], 0.10)
        self.assertIsNone(outcomes["return_3d"])
        self.assertIsNone(outcomes["return_5d"])
        self.assertTrue(outcomes["hit_plus_3pct_next_day"])
        self.assertTrue(outcomes["hit_minus_3pct_next_day"])

    def test_summarize_cohort_equal_weights_each_signal_date(self) -> None:
        picks = [
            {"date": "2026-07-01", "rank": 1, "return_1d": 0.10, "hit_plus_3pct_next_day": True, "hit_minus_3pct_next_day": False},
            {"date": "2026-07-01", "rank": 2, "return_1d": 0.00, "hit_plus_3pct_next_day": False, "hit_minus_3pct_next_day": False},
            {"date": "2026-07-02", "rank": 1, "return_1d": -0.10, "hit_plus_3pct_next_day": False, "hit_minus_3pct_next_day": True},
        ]

        summary = backtest.summarize_cohort(picks, top_n=3)

        self.assertEqual(summary["signal_days"], 2)
        self.assertAlmostEqual(summary["return_1d"]["mean"], -0.025)
        self.assertAlmostEqual(summary["return_1d"]["median"], -0.025)
        self.assertEqual(summary["return_1d"]["positive_ratio"], 0.5)
        self.assertAlmostEqual(summary["hit_plus_3pct_next_day"], 1 / 3)
        self.assertAlmostEqual(summary["hit_minus_3pct_next_day"], 1 / 3)

    def test_historical_fetch_uses_date_range_instead_of_latest_count(self) -> None:
        class FakeTq:
            def __init__(self) -> None:
                self.kwargs = {}

            def get_market_data(self, **kwargs: object) -> object:
                self.kwargs = kwargs
                return {"raw": "frames"}

        tq = FakeTq()
        expected_bars = [bar("2026-07-01", 10.0), bar("2026-07-02", 10.2)]
        with patch.object(backtest, "_find_field_frames", return_value={}), \
             patch.object(backtest, "_normalize_symbol_bars", return_value=expected_bars):
            bars, errors, stats = backtest.fetch_historical_bars(
                tq, ["000001.SZ"], "2026-01-01", "2026-07-31", batch_size=200
            )

        self.assertEqual(errors, [])
        self.assertEqual(bars["000001.SZ"], expected_bars)
        self.assertEqual(tq.kwargs["count"], 0)
        self.assertEqual(tq.kwargs["start_time"], "20260101")
        self.assertEqual(tq.kwargs["end_time"], "20260731")
        self.assertEqual(tq.kwargs["field_list"], ["Open", "High", "Low", "Close", "Volume", "Amount"])
        self.assertFalse(tq.kwargs["fill_data"])
        self.assertEqual(stats.fetch_requests, 1)

    def test_native_history_requests_dates_and_all_return_values(self) -> None:
        class FakeTq:
            def __init__(self) -> None:
                self.kwargs = {}

            def formula_process_mul_xg(self, **kwargs: object) -> object:
                self.kwargs = kwargs
                return {"000001.SZ": {"XG": [{"Date": "20260702", "Value": "1"}]}, "ErrorId": "0"}

        tq = FakeTq()
        matches, errors = backtest.run_native_history(
            tq,
            ["000001.SZ"],
            "ZHUAN",
            "14,28,57,114,3,21",
            "2026-01-01",
            "2026-07-31",
            batch_size=200,
        )

        self.assertEqual(errors, [])
        self.assertEqual(matches, {"2026-07-02": {"000001.SZ"}})
        self.assertEqual(tq.kwargs["count"], 0)
        self.assertEqual(tq.kwargs["return_count"], 0)
        self.assertTrue(tq.kwargs["return_date"])

    def test_build_payload_keeps_only_global_positive_top_five(self) -> None:
        args = backtest.parse_args(["--start-date", "2026-07-02", "--end-date", "2026-07-02"])
        sectors = [f"88050{index}.SH" for index in range(1, 6)]
        board_bars = {
            sector: [bar("2026-07-01", 10.0), bar("2026-07-02", 10.0 + index)]
            for index, sector in enumerate(sectors, start=1)
        }
        stock_bars = {
            "000001.SZ": [
                bar("2026-07-01", 10.0),
                bar("2026-07-02", 10.2, high=10.3, low=10.0),
                bar("2026-07-03", 10.4),
            ]
        }
        candidate = CandidateScore(
            symbol="000001.SZ",
            stock_name="测试股份",
            signal_source="shared",
            final_score=80.0,
            base_score=84.0,
            risk_penalty=4.0,
            factor_scores={"brick_strength": 28.0},
            hard_filter_reasons=(),
            risk_reasons=(),
            metrics={},
        )

        with patch.object(backtest, "fetch_concept_boards", return_value=sectors), \
             patch.object(backtest, "fetch_sector_names", return_value={sector: sector for sector in sectors}), \
             patch.object(backtest, "fetch_historical_bars", side_effect=[(board_bars, [], backtest.CacheStats()), (stock_bars, [], backtest.CacheStats())]), \
             patch.object(backtest, "fetch_sector_members", return_value={"000001.SZ": [sectors[-1]]}), \
             patch.object(backtest, "run_native_history", return_value=({"2026-07-02": {"000001.SZ"}}, [])), \
             patch.object(backtest, "historical_python_signals", return_value=({"2026-07-02": {"000001.SZ"}}, {("2026-07-02", "000001.SZ"): BrickSignal(2, 1, 2, 1, 1)})), \
             patch.object(backtest, "fetch_stock_names", return_value={"000001.SZ": "测试股份"}), \
             patch.object(backtest, "rank_sector_candidates", return_value=[candidate]):
            payload = backtest.build_backtest_payload(args, object())

        self.assertEqual(payload["top_limit"], 5)
        self.assertEqual(len(payload["picks"]), 1)
        self.assertEqual(payload["picks"][0]["rank"], 1)
        self.assertEqual(payload["picks"][0]["symbol"], "000001.SZ")
        self.assertEqual(payload["consensus_picks"][0]["symbol"], "000001.SZ")
        self.assertEqual(payload["consensus_cohorts"]["top_3"]["picks"], 1)
        report = backtest.render_backtest_markdown(payload)
        self.assertIn("Top 3", report)
        self.assertIn("双引擎共振组合", report)
        self.assertIn("当前板块成分", report)

    def test_write_outputs_keep_missing_returns_blank(self) -> None:
        pick = {
            "date": "2026-07-02",
            "rank": 1,
            "symbol": "000001.SZ",
            "stock_name": "测试股份",
            "signal_source": "shared",
            "best_sector": "880501.SH",
            "best_sector_name": "测试概念",
            "sectors": ["880501.SH"],
            "final_score": 80.0,
            "base_score": 84.0,
            "risk_penalty": 4.0,
            "factor_scores": {"brick_strength": 28.0},
            "score_bucket": "80-<90",
            "return_1d": 0.02,
            "return_2d": None,
            "return_3d": None,
            "return_5d": None,
            "next_day_high_return": 0.04,
            "next_day_low_return": -0.01,
            "hit_plus_3pct_next_day": True,
            "hit_minus_3pct_next_day": False,
        }
        payload = {
            "start_date": "2026-07-02",
            "end_date": "2026-07-02",
            "status": "complete",
            "valid_trading_days": 1,
            "signal_days": 1,
            "pick_count": 1,
            "formula_name": "ZHUAN",
            "formula_arg": "14,28,57,114,3,21",
            "execution_policy": "仅双引擎共振的正分 Top 3 可作为执行候选；单端信号仅观察。",
            "factor_weights": {"brick_strength": 30.0, "previous_kdj_j": 20.0},
            "picks": [pick],
            "cohorts": {
                "top_1": backtest.summarize_cohort([pick], 1),
                "top_3": backtest.summarize_cohort([pick], 3),
                "top_5": backtest.summarize_cohort([pick], 5),
            },
            "source_summaries": {
                key: backtest.summarize_cohort([pick] if key == "shared" else [], 5)
                for key in ("shared", "native_only", "python_only")
            },
            "score_summaries": {
                key: backtest.summarize_cohort([pick] if key == "80-<90" else [], 5)
                for key in ("<50", "50-<60", "60-<70", "70-<80", "80-<90", "90-100")
            },
            "cache": {
                "path": "data/tdx-brick-selector/backtest-cache.db",
                "refresh_requested": False,
                "concepts": {"cache_hit_symbols": 10, "fetched_symbols": 0},
                "stock_bars": {"cache_hit_symbols": 20, "fetched_symbols": 0},
            },
            "limitations": ["概念板块成分使用当前板块成分。"],
            "errors": [],
        }

        with tempfile.TemporaryDirectory() as directory:
            report_path, csv_path = backtest.write_backtest_outputs(payload, Path(directory))
            report = report_path.read_text(encoding="utf-8")
            csv_text = csv_path.read_text(encoding="utf-8-sig")
            consensus_csv_path = Path(directory) / "consensus-picks.csv"
            consensus_csv_text = consensus_csv_path.read_text(encoding="utf-8-sig")

        self.assertIn("Top 3（核心）", report)
        self.assertIn("日线缓存", report)
        self.assertIn("仅双引擎共振的正分 Top 3", report)
        self.assertIn("brick_strength=30", report)
        self.assertNotIn("nan", report.lower())
        data_row = csv_text.splitlines()[1].split(",")
        return_2d_index = csv_text.splitlines()[0].split(",").index("return_2d")
        self.assertEqual(data_row[return_2d_index], "")
        self.assertTrue(consensus_csv_text.startswith("date,rank,symbol"))

    def test_replay_context_precomputes_sector_members_and_date_indexes(self) -> None:
        stock_bars = {
            "000001.SZ": [bar("2026-07-01", 10.0), bar("2026-07-02", 10.2)],
            "600000.SH": [bar("2026-07-01", 20.0), bar("2026-07-03", 20.2)],
        }
        context = backtest.build_replay_context(
            concept_rankings={"2026-07-02": [("880501.SH", 2.0)]},
            sector_members={
                "000001.SZ": ["880501.SH"],
                "600000.SH": ["880501.SH", "880502.SH"],
            },
            stock_bars=stock_bars,
            native_matches={},
            python_matches={},
            python_signals={},
            sources={},
            stock_names={},
            sector_names={},
            formula_arg="14,28,57,114,3,21",
        )

        self.assertEqual(context.members_by_sector["880501.SH"], ("000001.SZ", "600000.SH"))
        self.assertEqual(context.date_indexes["000001.SZ"]["2026-07-02"], 1)
        window = backtest.HistoricalBarWindow(stock_bars["000001.SZ"], 1)
        self.assertEqual(len(window), 2)
        self.assertEqual(window[-1].date, "2026-07-02")
        self.assertEqual(window[-2:].copy(), stock_bars["000001.SZ"])

    def test_parse_args_accepts_worker_count_for_cpu_replay(self) -> None:
        args = backtest.parse_args(
            [
                "--start-date",
                "2026-01-01",
                "--end-date",
                "2026-06-30",
                "--workers",
                "2",
                "--refresh-cache",
                "--cache-path",
                "custom-cache.db",
            ]
        )

        self.assertEqual(args.workers, 2)
        self.assertTrue(args.refresh_cache)
        self.assertEqual(args.cache_path, Path("custom-cache.db"))

    def test_scoring_replay_can_use_isolated_processes_without_tq(self) -> None:
        context = backtest.build_replay_context(
            concept_rankings={
                "2026-07-01": [("880501.SH", 2.0)],
                "2026-07-02": [("880501.SH", 1.0)],
            },
            sector_members={},
            stock_bars={},
            native_matches={},
            python_matches={},
            python_signals={},
            sources={},
            stock_names={},
            sector_names={},
            formula_arg="14,28,57,114,3,21",
        )

        results, errors, workers_used = backtest.run_scoring_replay(context, workers=2)

        self.assertEqual([date for date, _, _, _ in results], ["2026-07-01", "2026-07-02"])
        self.assertTrue(all(rows == [] for _, rows, _, _ in results))
        self.assertTrue(
            not errors or errors[0].startswith("并行评分失败，已回退单进程:")
        )
        self.assertIn(workers_used, (1, 2))


if __name__ == "__main__":
    unittest.main()
