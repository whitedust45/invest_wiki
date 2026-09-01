#!/usr/bin/env python3
"""Offline tests for the pure TDX brick selector model."""

from __future__ import annotations

import math
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.short_term.strategies import brick as selector  # noqa: E402

from modules.short_term.strategies.brick import (  # noqa: E402
    apply_sector_rank_score_caps,
    b1_trend_metrics,
    b1_trend_component_scores,
    brick_strength_score,
    linear_factor_score,
    previous_kdj_j_metrics,
    previous_kdj_j_score,
    previous_kdj_doji_metrics,
    CandidateScore,
    DailyBar,
    SelectionResult,
    calculate_brick_series,
    diagnose_concepts,
    extract_symbols,
    extract_formula_matches,
    fetch_board_bars,
    fetch_concept_boards,
    fetch_sector_names,
    fetch_sector_members,
    has_unrecovered_distribution_bar,
    merge_sector_members,
    parse_market_snapshot,
    rank_by_change_pct,
    rank_sector_candidates,
    render_markdown_report,
    resolve_latest_concept_date,
    run_native_selection,
    run_python_selection,
    select_execution_candidates,
    select_top_concepts,
    sma,
    strict_brick_signal,
)


class FakeILoc:
    def __init__(self, rows: list[list[float]]) -> None:
        self.rows = rows

    def __getitem__(self, position: tuple[int, int]) -> float:
        row, column = position
        return self.rows[row][column]


class FakeFrame:
    def __init__(self, index: list[str], columns: list[str], rows: list[list[float]]) -> None:
        self.index = index
        self.columns = columns
        self.iloc = FakeILoc(rows)


class FakeTq:
    def __init__(self) -> None:
        self.market_data = {
            "HIGH": FakeFrame(
                ["2026-07-09", "2026-07-10"],
                ["880501.SH", "880502.SH"],
                [[11.0, 21.0], [12.0, 24.0]],
            ),
            "low": FakeFrame(
                ["2026-07-09", "2026-07-10"],
                ["880501.SH", "880502.SH"],
                [[9.0, 19.0], [10.0, 20.0]],
            ),
            "Close": FakeFrame(
                ["2026-07-09", "2026-07-10"],
                ["880501.SH", "880502.SH"],
                [[10.0, 20.0], [11.0, 23.0]],
            ),
            "Open": FakeFrame(
                ["2026-07-09", "2026-07-10"],
                ["880501.SH", "880502.SH"],
                [[9.5, 19.5], [10.5, 20.5]],
            ),
            "Volume": FakeFrame(
                ["2026-07-09", "2026-07-10"],
                ["880501.SH", "880502.SH"],
                [[100.0, 200.0], [150.0, 250.0]],
            ),
            "Amount": FakeFrame(
                ["2026-07-09", "2026-07-10"],
                ["880501.SH", "880502.SH"],
                [[1000.0, 4000.0], [1650.0, 5750.0]],
            ),
        }
        self.market_requests: list[tuple[list[str], int]] = []
        self.formula_requests: list[list[str]] = []
        self.refreshed_markets: list[str] = []
        self.sector_list_requests: list[int] = []

    def refresh_cache(self, market: str) -> None:
        self.refreshed_markets.append(market)

    def get_stock_list(self, market: str) -> list[str]:
        raise AssertionError("概念板块列表不应通过 get_stock_list 获取")

    def get_sector_list(self, list_type: int) -> list[str]:
        assert list_type == 1
        self.sector_list_requests.append(list_type)
        return [
            {"Code": "880501.SH", "Name": "概念一"},
            {"Code": "880502.SH", "Name": "概念二"},
            {"Code": "880201.SH", "Name": "行业一"},
        ]

    def get_stock_list_in_sector(self, code: str) -> list[str]:
        return {"880501.SH": ["000001.SZ"], "880502.SH": ["000001.SZ", "600000.SH"]}[code]

    def get_market_data(
        self, stock_list: list[str], period: str, count: int, dividend_type: str
    ) -> dict[str, FakeFrame]:
        assert period == "1d"
        assert dividend_type == "none"
        self.market_requests.append((stock_list, count))
        return self.market_data

    def formula_process_mul_xg(
        self,
        formula_name: str,
        stock_list: list[str],
        stock_period: str,
        return_count: int,
        formula_arg: str = "",
    ) -> object:
        assert formula_name == "ZHUAN"
        assert formula_arg in ("", "14,28,57,114,3,21")
        assert stock_period == "1d"
        assert return_count == 1
        self.formula_requests.append(stock_list)
        if stock_list == ["600000.SH"]:
            raise RuntimeError("batch unavailable")
        return {symbol: {"XG": ["1"]} for symbol in stock_list}

    def get_market_snapshot(self, stock_code: str) -> dict[str, str]:
        return {"Name": f"名称{stock_code[:3]}", "ZuiXinJia": "10.00", "ZhangTingJia": "10.99"}

    def get_stock_info(self, stock_code: str) -> dict[str, str]:
        return {"Name": f"名称{stock_code[:3]}"}


class TdxBrickSelectorTests(unittest.TestCase):
    def test_factor_weights_match_the_approved_hierarchy(self) -> None:
        self.assertEqual(
            selector.FACTOR_WEIGHTS,
            {
                "board_leadership": 15.0,
                "relative_strength": 10.0,
                "liquidity": 6.0,
                "tail_structure": 0.0,
                "brick_strength": 30.0,
                "white_yellow_trend": 14.0,
                "previous_kdj_j": 20.0,
                "previous_kdj_doji": 5.0,
            },
        )
        self.assertEqual(sum(selector.FACTOR_WEIGHTS.values()), 100.0)

    def test_linear_factor_score_is_continuous_between_bounds(self) -> None:
        self.assertEqual(linear_factor_score(-1.0, -1.0, 1.0, 8.0), 0.0)
        self.assertEqual(linear_factor_score(0.0, -1.0, 1.0, 8.0), 4.0)
        self.assertEqual(linear_factor_score(1.0, -1.0, 1.0, 8.0), 8.0)
        self.assertEqual(linear_factor_score(2.0, -1.0, 1.0, 8.0), 8.0)

    def test_white_yellow_component_scores_are_continuous_and_sum_to_14(self) -> None:
        neutral = b1_trend_component_scores(0.0, 0.0, 0.0, 0.0)
        strong = b1_trend_component_scores(1.0, 0.5, 0.3, 1.0)

        self.assertEqual(neutral, {"gap": 3.2941176470588234, "white_slope": 1.6470588235294117, "yellow_slope": 1.2352941176470589, "close_gap": 0.8235294117647058})
        self.assertEqual(sum(strong.values()), 14.0)

    def test_previous_kdj_j_uses_continuous_curve_with_12_as_slope_break(self) -> None:
        expected = {0.0: 20.0, 6.0: 18.0, 12.0: 16.0, 18.0: 10.666666666666668, 24.0: 5.333333333333334, 30.0: 0.0}
        for value, score in expected.items():
            self.assertAlmostEqual(previous_kdj_j_score(value), score)
        self.assertAlmostEqual(previous_kdj_j_score(12.0 - 1e-6), previous_kdj_j_score(12.0 + 1e-6), places=5)
        slow_drop = previous_kdj_j_score(6.0) - previous_kdj_j_score(7.0)
        fast_drop = previous_kdj_j_score(18.0) - previous_kdj_j_score(19.0)
        self.assertLess(slow_drop, fast_drop)

    def test_previous_kdj_j_and_doji_reward_low_adjustment(self) -> None:
        oversold = [
            DailyBar(date=f"2026-02-{index:03d}", open=float(200 - index), high=float(202 - index), low=float(200 - index), close=float(200 - index))
            for index in range(1, 30)
        ]
        extended = list(reversed(oversold))

        low_j = previous_kdj_j_metrics(oversold)
        high_j = previous_kdj_j_metrics(extended)

        self.assertLessEqual(low_j["previous_j"], 12.0)
        self.assertGreaterEqual(low_j["score"], 12.0)
        self.assertGreater(high_j["previous_j"], 30.0)
        self.assertEqual(high_j["score"], 0.0)
        self.assertEqual(previous_kdj_doji_metrics(oversold, low_j["previous_j"])["score"], 5.0)
        self.assertEqual(previous_kdj_doji_metrics(extended, high_j["previous_j"])["score"], 0.0)
        self.assertEqual(previous_kdj_doji_metrics(oversold, 12.0)["score"], 0.0)
    def test_b1_white_above_yellow_trend_factor_rewards_confirmed_uptrend(self) -> None:
        uptrend = [
            DailyBar(date=f"2026-01-{index:03d}", open=float(index), high=float(index + 1), low=float(index - 1), close=float(index))
            for index in range(1, 130)
        ]
        downtrend = list(reversed(uptrend))

        confirmed = b1_trend_metrics(uptrend, "14,28,57,114,3,21")
        broken = b1_trend_metrics(downtrend, "14,28,57,114,3,21")

        self.assertTrue(confirmed["white_above_yellow"])
        self.assertEqual(confirmed["score"], 14.0)
        self.assertFalse(broken["white_above_yellow"])
        self.assertEqual(broken["score"], 0.0)
    def test_sector_rank_score_caps_keep_only_top_three_as_high_scoring(self) -> None:
        candidates = [
            CandidateScore(f"00000{index}.SZ", "", "shared", 90.0 - index, 90.0 - index, 0.0, {}, (), (), {})
            for index in range(1, 7)
        ]

        capped = apply_sector_rank_score_caps(candidates)

        self.assertEqual([item.final_score for item in capped[:3]], [89.0, 88.0, 87.0])
        self.assertEqual(capped[3].final_score, 45.0)
        self.assertEqual(capped[4].final_score, 35.0)
        self.assertEqual(capped[5].final_score, 25.0)
        self.assertEqual(capped[3].sector_rank, 4)
        self.assertEqual(capped[3].sector_score_cap, 45.0)
    def test_sma_matches_tdx_m_equals_one_recurrence(self) -> None:
        self.assertEqual(sma([1.0, 2.0, 3.0], period=4), [1.0, 1.25, 1.6875])

    def test_brick_strength_uses_continuous_three_quarters_curve(self) -> None:
        expected = {0.75: 0.0, 1.0: 6.0, 1.5: 18.0, 2.0: 30.0, 3.0: 30.0}
        for ratio, score in expected.items():
            self.assertAlmostEqual(brick_strength_score(ratio, 1.0), score)

    def test_strict_signal_requires_yesterday_green_and_three_quarters_strength(self) -> None:
        signal = strict_brick_signal([2.0, 1.0, 2.0])

        self.assertIsNotNone(signal)
        assert signal is not None
        self.assertEqual(signal.today_red_length, 1.0)
        self.assertEqual(signal.yesterday_green_length, 1.0)

    def test_strict_signal_rejects_equal_yesterday_bar(self) -> None:
        self.assertIsNone(strict_brick_signal([1.0, 1.0, 2.0]))

    def test_strict_signal_rejects_exact_three_quarters_strength(self) -> None:
        self.assertIsNone(strict_brick_signal([4.0, 0.0, 3.0]))
        self.assertIsNotNone(strict_brick_signal([4.0, 0.0, 3.01]))

    def test_strict_signal_rejects_nonfinite_brick_values(self) -> None:
        self.assertIsNone(strict_brick_signal([float("nan"), float("nan"), float("nan")]))

    def test_calculate_brick_series_starts_at_fourth_bar(self) -> None:
        bars = [
            DailyBar("2026-07-01", 10.0, 0.0, 5.0),
            DailyBar("2026-07-02", 11.0, 0.0, 6.0),
            DailyBar("2026-07-03", 12.0, 0.0, 7.0),
            DailyBar("2026-07-04", 13.0, 0.0, 8.0),
        ]

        bricks = calculate_brick_series(bars)

        self.assertEqual(len(bricks), 1)
        self.assertTrue(math.isclose(bricks[0], 109.07692307692308))

    def test_zero_four_bar_range_has_no_brick_signal(self) -> None:
        bars = [DailyBar("2026-07-01", 10.0, 10.0, 10.0)] * 4

        self.assertEqual(calculate_brick_series(bars), [])

    def test_any_zero_four_bar_range_invalidates_entire_series(self) -> None:
        bars = [
            DailyBar("2026-07-01", 10.0, 0.0, 5.0),
            DailyBar("2026-07-02", 11.0, 0.0, 6.0),
            DailyBar("2026-07-03", 12.0, 0.0, 7.0),
            DailyBar("2026-07-04", 13.0, 0.0, 8.0),
            DailyBar("2026-07-05", 13.0, 13.0, 13.0),
            DailyBar("2026-07-06", 13.0, 13.0, 13.0),
            DailyBar("2026-07-07", 13.0, 13.0, 13.0),
            DailyBar("2026-07-08", 13.0, 13.0, 13.0),
        ]

        self.assertEqual(calculate_brick_series(bars), [])

    def test_fewer_than_four_bars_produce_no_brick_values(self) -> None:
        bars = [DailyBar("2026-07-01", 10.0, 0.0, 5.0)] * 3

        self.assertEqual(calculate_brick_series(bars), [])

    def test_rank_and_member_merge_preserve_sector_provenance(self) -> None:
        ranked = rank_by_change_pct(
            {"880501.SH": [10.0, 11.0], "880502.SH": [10.0, 10.5]},
            1,
        )

        self.assertEqual([symbol for symbol, _ in ranked], ["880501.SH"])
        self.assertAlmostEqual(ranked[0][1], 10.0)
        self.assertEqual(
            merge_sector_members(
                {
                    "880501.SH": ["000001.SZ", "600000.SH", "000001.SZ"],
                    "880502.SH": ["000001.SZ"],
                }
            ),
            {
                "000001.SZ": ["880501.SH", "880502.SH"],
                "600000.SH": ["880501.SH"],
            },
        )

    def test_rank_sorts_equal_changes_by_symbol(self) -> None:
        ranked = rank_by_change_pct(
            {"880502.SH": [10.0, 11.0], "880501.SH": [10.0, 11.0]},
            2,
        )

        self.assertEqual([symbol for symbol, _ in ranked], ["880501.SH", "880502.SH"])
        self.assertAlmostEqual(ranked[0][1], 10.0)
        self.assertAlmostEqual(ranked[1][1], 10.0)

    def test_rank_uses_unrounded_change_before_symbol_tiebreak(self) -> None:
        ranked = rank_by_change_pct(
            {
                "880502.SH": [10.0, 11.000000000004],
                "880501.SH": [10.0, 11.000000000003],
            },
            2,
        )

        self.assertEqual([symbol for symbol, _ in ranked], ["880502.SH", "880501.SH"])
        self.assertGreater(ranked[0][1], ranked[1][1])

    def test_extract_symbols_recurses_through_nested_standard_types(self) -> None:
        raw = {
            "Value": {"matches": ["000001.SZ"], "other": {"Code": "600000.SH"}},
            "300001.SZ": {"XG": ["1"]},
        }

        self.assertEqual(extract_symbols(raw), {"000001.SZ", "300001.SZ", "600000.SH"})

    def test_native_formula_matches_use_latest_xg_value(self) -> None:
        raw = {
            "000001.SZ": {"XG": ["0"]},
            "600000.SH": {"XG": ["1"]},
        }

        self.assertEqual(
            extract_formula_matches(raw, ["000001.SZ", "600000.SH"]),
            {"600000.SH"},
        )

    def test_native_formula_does_not_match_symbol_entries_without_xg(self) -> None:
        raw = {"000001.SZ": {"砖型图": ["1"]}}

        self.assertEqual(extract_formula_matches(raw, ["000001.SZ"]), set())

    def test_concept_members_are_fetched_without_a_share_scan(self) -> None:
        tq = FakeTq()

        self.assertEqual(fetch_concept_boards(tq, r"F:\\missing-tdx"), ["880501.SH", "880502.SH"])
        self.assertEqual(tq.refreshed_markets, ["AG"])
        self.assertEqual(tq.sector_list_requests, [1])
        self.assertEqual(
            fetch_sector_members(tq, ["880501.SH", "880502.SH"]),
            {
                "000001.SZ": ["880501.SH", "880502.SH"],
                "600000.SH": ["880502.SH"],
            },
        )

    def test_concept_diagnostics_counts_each_data_boundary(self) -> None:
        tq = FakeTq()

        diagnostics = diagnose_concepts(
            tq, expected_date="2026-07-10", batch_size=200, tdx_path=r"F:\\missing-tdx"
        )

        self.assertEqual(diagnostics["all_sector_count"], 2)
        self.assertEqual(diagnostics["concept_code_count"], 2)
        self.assertEqual(diagnostics["daily_bar_count"], 2)
        self.assertEqual(diagnostics["requested_date"], "2026-07-10")
        self.assertEqual(diagnostics["data_date"], "2026-07-10")
        self.assertEqual(diagnostics["current_day_concept_count"], 2)
        self.assertEqual(diagnostics["latest_date_counts"], {"2026-07-10": 2})
        self.assertEqual(
            diagnostics["sector_prefix_counts"], {"8805": 2}
        )
        self.assertEqual(
            diagnostics["all_sector_samples"],
            ["880501.SH", "880502.SH"],
        )
        self.assertEqual(diagnostics["errors"], [])

    def test_preflight_falls_back_to_latest_trading_date_on_weekend(self) -> None:
        args = selector.parse_args(["--concept-limit", "2"])

        with patch.object(selector, "_current_date", return_value="2026-07-11"):
            payload = selector.build_preflight_payload(args, FakeTq())

        self.assertTrue(payload["ready"])
        self.assertEqual(payload["reason"], "ready")
        self.assertEqual(payload["requested_date"], "2026-07-11")
        self.assertEqual(payload["data_date"], "2026-07-10")
        self.assertTrue(payload["fallback_used"])
        self.assertEqual(payload["current_day_concept_count"], 2)

    def test_latest_concept_date_requires_enough_boards_on_the_same_day(self) -> None:
        bars = {
            "880501.SH": [
                DailyBar("2026-07-09", 11.0, 9.0, 10.0),
                DailyBar("2026-07-10", 12.0, 10.0, 11.0),
            ],
            "880502.SH": [
                DailyBar("2026-07-08", 21.0, 19.0, 20.0),
                DailyBar("2026-07-09", 22.0, 20.0, 21.0),
            ],
        }

        self.assertEqual(resolve_latest_concept_date(bars, minimum_concept_count=1), "2026-07-10")
        self.assertIsNone(resolve_latest_concept_date(bars, minimum_concept_count=2))

    def test_board_bars_normalize_field_dataframes_and_rank_current_boards(self) -> None:
        tq = FakeTq()

        bars, errors = fetch_board_bars(tq, ["880501.SH", "880502.SH"], count=2)

        self.assertEqual(errors, [])
        self.assertEqual(tq.market_requests, [(["880501.SH", "880502.SH"], 2)])
        self.assertEqual(
            bars["880501.SH"][-1],
            DailyBar("2026-07-10", 12.0, 10.0, 11.0, 10.5, 150.0, 1650.0),
        )
        ranked = select_top_concepts(bars, concept_limit=1, expected_date="2026-07-10")
        self.assertEqual([symbol for symbol, _ in ranked], ["880502.SH"])
        self.assertAlmostEqual(ranked[0][1], 15.0)

    def test_board_bars_skip_early_nonfinite_rows_when_later_data_is_valid(self) -> None:
        tq = FakeTq()
        tq.market_data["HIGH"].iloc.rows[1][0] = float("nan")

        bars, errors = fetch_board_bars(tq, ["880501.SH"], count=2)

        self.assertEqual(errors, [])
        self.assertEqual(len(bars["880501.SH"]), 1)
        self.assertEqual(bars["880501.SH"][0].date, "2026-07-09")

    def test_concept_ranking_rejects_insufficient_current_data(self) -> None:
        bars = {
            "880501.SH": [
                DailyBar("2026-07-09", 11.0, 9.0, 10.0),
                DailyBar("2026-07-09", 12.0, 10.0, 11.0),
            ]
        }

        with self.assertRaisesRegex(RuntimeError, "有效概念板块"):
            select_top_concepts(bars, concept_limit=1, expected_date="2026-07-10")

    def test_native_selection_extracts_matches_and_continues_after_batch_error(self) -> None:
        tq = FakeTq()

        result = run_native_selection(
            tq,
            ["000001.SZ", "600000.SH", "300001.SZ"],
            formula_name="ZHUAN",
            batch_size=1,
        )

        self.assertIsInstance(result, SelectionResult)
        self.assertEqual(result.matches, {"000001.SZ", "300001.SZ"})
        self.assertEqual(result.signals, {})
        self.assertEqual(len(result.errors), 1)
        self.assertIn("600000.SH", result.errors[0])

    def test_python_selection_rejects_stale_or_insufficient_bars_with_errors(self) -> None:
        result = run_python_selection(
            {
                "000001.SZ": [DailyBar("2026-07-09", 11.0, 9.0, 10.0)],
                "600000.SH": [
                    DailyBar("2026-07-08", 11.0, 9.0, 10.0),
                    DailyBar("2026-07-09", 12.0, 10.0, 11.0),
                ],
            },
            expected_date="2026-07-10",
        )

        self.assertEqual(result.matches, set())
        self.assertEqual(result.signals, {})
        self.assertEqual(len(result.errors), 2)

    def test_cli_defaults_match_approved_execution_scope(self) -> None:
        args = selector.parse_args([])

        self.assertEqual(args.engine, "both")
        self.assertEqual(args.concept_limit, 5)
        self.assertEqual(args.formula_name, "ZHUAN")
        self.assertEqual(args.formula_arg, "14,28,57,114,3,21")
        self.assertEqual(args.tdx_path, r"F:\new_tdx64")
        self.assertEqual(args.batch_size, 200)
        self.assertFalse(args.diagnose)

    def test_cli_diagnose_flag_is_opt_in(self) -> None:
        self.assertTrue(selector.parse_args(["--diagnose"]).diagnose)

    def test_comparison_reports_shared_and_engine_only_symbols(self) -> None:
        self.assertEqual(
            selector.compare_matches(
                {"000001.SZ", "600000.SH"},
                {"000001.SZ", "300001.SZ"},
            ),
            {
                "shared": ["000001.SZ"],
                "native_only": ["600000.SH"],
                "python_only": ["300001.SZ"],
            },
        )

    def test_parse_market_snapshot_accepts_tdx_price_aliases(self) -> None:
        snapshot = parse_market_snapshot(
            {"Name": "测试股份", "ZuiXinJia": "10.00", "ZhangTingJia": "10.99"}
        )

        self.assertIsNotNone(snapshot)
        assert snapshot is not None
        self.assertEqual(snapshot.last_price, 10.0)
        self.assertEqual(snapshot.upper_limit, 10.99)
        self.assertEqual(snapshot.stock_name, "测试股份")

    def test_sector_names_are_read_from_tq_sector_list(self) -> None:
        self.assertEqual(
            fetch_sector_names(FakeTq()),
            {"880201.SH": "行业一", "880501.SH": "概念一", "880502.SH": "概念二"},
        )

    def test_unrecovered_distribution_bar_requires_volume_bear_candle_and_unrecovered_high(self) -> None:
        bars = [
            DailyBar(f"2026-07-0{index + 1}", 10.2, 9.8, 10.0, 10.0, 100.0, 1000.0)
            for index in range(5)
        ]
        bars.extend(
            [
                DailyBar("2026-07-06", 11.0, 9.0, 9.2, 10.8, 300.0, 3000.0),
                DailyBar("2026-07-07", 10.5, 9.8, 10.2, 10.0, 150.0, 1500.0),
            ]
        )

        self.assertTrue(has_unrecovered_distribution_bar(bars))
        recovered = list(bars)
        recovered[-1] = DailyBar("2026-07-07", 11.2, 9.8, 11.0, 10.0, 150.0, 1500.0)
        self.assertFalse(has_unrecovered_distribution_bar(recovered))

    def test_sector_ranking_keeps_hard_filter_candidate_at_zero(self) -> None:
        bars = [
            DailyBar(f"2026-07-{index + 1:02d}", 10.2, 9.8, 10.0, 10.0, 100.0, 1000.0)
            for index in range(6)
        ]
        bars[-1] = DailyBar("2026-07-06", 11.0, 10.0, 10.9, 10.2, 300.0, 3000.0)
        signals = {
            "000001.SZ": selector.BrickSignal(4.0, 2.0, 5.0, 3.0, 2.0),
            "600000.SH": selector.BrickSignal(4.0, 2.0, 5.0, 3.0, 2.0),
        }
        ranked = rank_sector_candidates(
            sector="880501.SH",
            board_rank=1,
            board_count=5,
            candidate_symbols=["000001.SZ", "600000.SH"],
            stock_bars={"000001.SZ": bars, "600000.SH": bars},
            signals=signals,
            snapshots={
                "000001.SZ": selector.MarketSnapshot(10.9, 10.9),
                "600000.SH": selector.MarketSnapshot(10.8, 10.9),
            },
        )

        self.assertEqual(ranked[0].symbol, "600000.SH")
        blocked = next(item for item in ranked if item.symbol == "000001.SZ")
        self.assertEqual(blocked.final_score, 0.0)
        self.assertIn("tail_limit_up_locked", blocked.hard_filter_reasons)

    def test_native_only_candidate_keeps_scoring_when_python_strict_signal_is_absent(self) -> None:
        bars = [
            DailyBar(f"2026-07-{index + 1:02d}", 10.2, 9.8, 10.0, 10.0, 100.0, 1000.0)
            for index in range(6)
        ]
        bars[-1] = DailyBar("2026-07-06", 11.0, 10.0, 10.8, 10.2, 300.0, 3000.0)

        ranked = rank_sector_candidates(
            sector="880501.SH",
            board_rank=1,
            board_count=5,
            candidate_symbols=["000001.SZ"],
            stock_bars={"000001.SZ": bars},
            signals={},
            snapshots={"000001.SZ": selector.MarketSnapshot(10.8, 10.99)},
            native_matches={"000001.SZ"},
            python_matches=set(),
        )

        self.assertGreater(ranked[0].final_score, 0.0)
        self.assertEqual(ranked[0].signal_source, "native_only")
        self.assertNotIn("strict_brick_signal_missing", ranked[0].hard_filter_reasons)

    def test_run_payload_falls_back_to_latest_trading_date_on_weekend(self) -> None:
        with patch.object(selector, "_current_date", return_value="2026-07-11"):
            payload = selector.build_run_payload(
                selector.parse_args(["--concept-limit", "2", "--engine", "both"]),
                FakeTq(),
            )

        self.assertEqual(payload["requested_date"], "2026-07-11")
        self.assertEqual(payload["data_date"], "2026-07-10")
        self.assertTrue(payload["fallback_used"])
        self.assertEqual(len(payload["ranking"]), 2)
        ranked_candidates = [
            candidate
            for board in payload["ranking"]
            for candidate in board["candidates"]
        ]
        self.assertTrue(ranked_candidates)
        self.assertTrue(all(candidate["final_score"] == 0.0 for candidate in ranked_candidates))
        self.assertTrue(
            any("daily_data_missing" in candidate["hard_filter_reasons"] for candidate in ranked_candidates)
        )
        self.assertTrue(all(candidate["stock_name"] for candidate in ranked_candidates))
        self.assertIn('"ranking"', selector.render_payload(payload, as_json=True))

    def test_markdown_report_deduplicates_symbols_by_best_score_and_merges_sectors(self) -> None:
        payload = {
            "data_date": "2026-07-10",
            "generated_at": "2026-07-10T14:40:00",
            "run_time": "14:40",
            "formula_name": "ZHUAN",
            "formula_arg": "14,28,57,114,3,21",
            "concepts": [
                {"symbol": "880501.SH", "name": "概念一", "change_pct": 4.0},
                {"symbol": "880502.SH", "name": "概念二", "change_pct": 3.0},
            ],
            "sector_members": {"000001.SZ": ["880501.SH", "880502.SH"], "600000.SH": ["880502.SH"]},
            "sector_names": {"880501.SH": "概念一", "880502.SH": "概念二"},
            "ranking": [
                {
                    "sector": "880501.SH",
                    "sector_name": "概念一",
                    "board_rank": 1,
                    "candidates": [
                        {
                            "symbol": "000001.SZ", "stock_name": "平安银行", "signal_source": "python_only",
                            "final_score": 80.0, "factor_scores": {"board_leadership": 25.0, "relative_strength": 15.0, "liquidity": 12.0, "tail_structure": 13.0, "brick_strength": 25.0},
                            "risk_penalty": 10.0, "hard_filter_reasons": [], "risk_reasons": ["daily_gain_at_least_8pct"],
                        },
                    ],
                },
                {
                    "sector": "880502.SH",
                    "sector_name": "概念二",
                    "board_rank": 2,
                    "candidates": [
                        {
                            "symbol": "000001.SZ", "stock_name": "平安银行", "signal_source": "python_only",
                            "final_score": 70.0, "factor_scores": {"board_leadership": 20.0, "relative_strength": 12.0, "liquidity": 10.0, "tail_structure": 13.0, "brick_strength": 25.0},
                            "risk_penalty": 10.0, "hard_filter_reasons": [], "risk_reasons": [],
                        },
                        {
                            "symbol": "600000.SH", "stock_name": "浦发银行", "signal_source": "shared",
                            "final_score": 75.0, "factor_scores": {"board_leadership": 20.0, "relative_strength": 15.0, "liquidity": 12.0, "tail_structure": 13.0, "brick_strength": 25.0},
                            "risk_penalty": 10.0, "hard_filter_reasons": [], "risk_reasons": [],
                        },
                    ],
                },
            ],
            "comparison": {"shared": ["600000.SH"], "native_only": [], "python_only": ["000001.SZ"]},
            "errors": [],
        }

        report = render_markdown_report(payload)

        self.assertIn("执行候选（双引擎共振 Top 3）", report)
        self.assertIn("观察候选总表（含单端信号）", report)
        self.assertIn("| 1 | 600000.SH | 浦发银行 | 概念二 (880502.SH) | 75.00 |", report)
        self.assertIn("| 1 | 000001.SZ | 平安银行 | Python | 概念一 (880501.SH) | 概念一 (880501.SH)、概念二 (880502.SH) | 80.00", report)
        self.assertIn("| 2 | 600000.SH | 浦发银行 | 双引擎 | 概念二 (880502.SH) | 概念二 (880502.SH) | 75.00", report)
        self.assertEqual(report.count("| 1 | 000001.SZ |"), 1)

    def test_execution_candidates_only_keep_positive_shared_top_three(self) -> None:
        payload = {
            "ranking": [
                {
                    "sector": "880501.SH",
                    "sector_name": "概念一",
                    "board_rank": 1,
                    "candidates": [
                        {"symbol": "000001.SZ", "signal_source": "python_only", "final_score": 99.0},
                        {"symbol": "000002.SZ", "signal_source": "shared", "final_score": 95.0},
                        {"symbol": "000003.SZ", "signal_source": "shared", "final_score": 90.0},
                    ],
                },
                {
                    "sector": "880502.SH",
                    "sector_name": "概念二",
                    "board_rank": 2,
                    "candidates": [
                        {"symbol": "000004.SZ", "signal_source": "shared", "final_score": 85.0},
                        {"symbol": "000005.SZ", "signal_source": "shared", "final_score": 80.0},
                        {"symbol": "000006.SZ", "signal_source": "shared", "final_score": 0.0},
                    ],
                },
            ]
        }

        selected = select_execution_candidates(payload)

        self.assertEqual([row["symbol"] for row in selected], ["000002.SZ", "000003.SZ", "000004.SZ"])
        self.assertTrue(all(row["signal_source"] == "shared" for row in selected))
        self.assertTrue(all(row["final_score"] > 0 for row in selected))

    def test_markdown_report_marks_weekend_data_date_fallback(self) -> None:
        payload = {
            "data_date": "2026-07-10",
            "requested_date": "2026-07-11",
            "fallback_used": True,
            "run_time": "14:40",
            "formula_name": "ZHUAN",
            "formula_arg": "14,28,57,114,3,21",
            "concepts": [],
            "sector_names": {},
            "sector_members": {},
            "ranking": [],
            "errors": [],
        }

        report = render_markdown_report(payload)

        self.assertIn("- 数据日期：2026-07-10", report)
        self.assertIn("- 自然日：2026-07-11（自动回退至最近交易日）", report)


if __name__ == "__main__":
    unittest.main()
