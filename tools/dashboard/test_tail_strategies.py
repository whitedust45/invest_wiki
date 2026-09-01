#!/usr/bin/env python3
"""Offline tests for the A-share tail strategy suite."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.short_term.strategies import tail  # noqa: E402
from modules.short_term.strategies.brick import DailyBar  # noqa: E402
from modules.short_term.registry import get_strategy  # noqa: E402
from modules.short_term.backtest_tail import (  # noqa: E402
    BacktestConfig,
    Position,
    Trade,
    build_report_payload,
    execute_exit,
    kelly_fraction,
    run_backtest_from_bars,
    main as backtest_main,
    write_backtest_outputs,
)


def bar(day: int, close: float) -> DailyBar:
    return DailyBar(
        date=f"2026-01-{day:02d}",
        open=close * 0.998,
        high=close * 1.01,
        low=close * 0.99,
        close=close,
        volume=10_000.0,
        amount=100_000_000.0,
    )


class TailStrategyTests(unittest.TestCase):
    def test_registers_exactly_four_strategy_families(self) -> None:
        self.assertEqual(
            [item.strategy_id for item in tail.available_tail_strategies()],
            [
                "steady_momentum",
                "trend_confirmation",
                "macd_divergence",
                "cup_handle_breakout",
            ],
        )

    def test_sma_and_ema_keep_input_length(self) -> None:
        self.assertEqual(tail.sma([1.0, 2.0, 3.0], 2), [1.0, 1.5, 2.5])
        self.assertAlmostEqual(tail.ema([1.0, 2.0, 3.0], 2)[-1], 2.5555555556)

    def test_candidate_selection_orders_by_score_and_caps_at_three(self) -> None:
        candidates = [
            tail.TailCandidate("steady_momentum", f"00000{index}.SZ", score, {}, -0.04, 0.08, 10, "ma5")
            for index, score in enumerate([30.0, 90.0, 60.0, 80.0], start=1)
        ]

        selected = tail.select_top_candidates(candidates)

        self.assertEqual([item.symbol for item in selected], ["000002.SZ", "000004.SZ", "000003.SZ"])
        self.assertTrue(all(item.max_holding_days <= 10 for item in selected))

    def test_strategies_reject_insufficient_daily_bar_history(self) -> None:
        bars = [bar(index, 10.0 + index * 0.1) for index in range(1, 11)]

        for strategy in tail.available_tail_strategies():
            self.assertIsNone(tail.score_strategy(strategy.strategy_id, "000001.SZ", bars, bars))

    def test_rsi_and_macd_handle_flat_and_rising_prices(self) -> None:
        flat_rsi = tail.rsi([10.0] * 20, 14)
        rising_rsi = tail.rsi([float(index) for index in range(1, 31)], 14)
        macd = tail.macd([float(index) for index in range(1, 41)])

        self.assertEqual(flat_rsi[-1], 50.0)
        self.assertGreater(rising_rsi[-1], 90.0)
        self.assertEqual(set(macd), {"dif", "dea", "hist"})
        self.assertGreater(macd["dif"][-1], macd["dea"][-1])

    def test_bullish_divergence_requires_lower_price_low_and_higher_dif_low(self) -> None:
        self.assertTrue(
            tail.has_bullish_divergence(
                closes=[10.0, 9.0, 10.2, 9.5, 8.5, 10.4],
                dif_values=[-0.1, -1.0, -0.2, -0.3, -0.6, 0.1],
            )
        )
        self.assertFalse(
            tail.has_bullish_divergence(
                closes=[10.0, 9.0, 10.2, 9.5, 8.5, 10.4],
                dif_values=[-0.1, -1.0, -0.2, -0.3, -1.2, 0.1],
            )
        )

    def test_cup_handle_breakout_requires_shrinking_handle_amount(self) -> None:
        closes = [10.0, 11.0, 12.0, 13.0, 12.0, 11.0, 10.5, 11.0, 12.0, 12.8, 12.4, 12.2, 12.4, 13.1]
        self.assertTrue(tail.is_cup_handle_breakout(closes, [100.0] * 10 + [40.0] * 4))
        self.assertFalse(tail.is_cup_handle_breakout(closes, [100.0] * 10 + [150.0] * 4))

    def test_steady_momentum_emits_a_tail_candidate_with_fixed_exit_policy(self) -> None:
        bars = [bar(index, 10.0 + index * 0.05) for index in range(1, 71)]

        candidate = tail.score_strategy("steady_momentum", "000001.SZ", bars)

        self.assertIsNotNone(candidate)
        assert candidate is not None
        self.assertEqual(candidate.strategy_id, "steady_momentum")
        self.assertEqual(candidate.stop_loss_pct, -0.04)
        self.assertEqual(candidate.take_profit_pct, 0.08)
        self.assertEqual(candidate.max_holding_days, 10)

    def test_trend_confirmation_requires_market_bull_state_and_returns_candidate(self) -> None:
        prices = [10.0 + index * 0.05 + [0.08, -0.04, 0.04, -0.08][index % 4] for index in range(1, 71)]
        bars = [bar(index, price) for index, price in enumerate(prices, start=1)]
        market = [bar(index, 3_000.0 + index * 2.0) for index in range(1, 71)]

        candidate = tail.score_strategy("trend_confirmation", "000001.SZ", bars, market)

        self.assertIsNotNone(candidate)
        assert candidate is not None
        self.assertEqual(candidate.stop_loss_pct, -0.05)
        self.assertEqual(candidate.take_profit_pct, 0.10)
        self.assertEqual(candidate.exit_rule, "macd_turns_weak")

    def test_macd_divergence_emits_candidate_after_upward_confirmation(self) -> None:
        prices = [15.0 - index * 0.05 for index in range(60)] + [
            12.0, 11.5, 10.0, 10.8, 11.8, 12.5, 12.7, 12.0, 11.6, 10.8, 9.8, 10.5, 11.0, 11.3,
        ]
        bars = [bar(index, price) for index, price in enumerate(prices, start=1)]
        market = [bar(index, 3_000.0 + index * 2.0) for index in range(1, len(bars) + 1)]

        candidate = tail.score_strategy("macd_divergence", "000001.SZ", bars, market)

        self.assertIsNotNone(candidate)
        assert candidate is not None
        self.assertEqual(candidate.stop_loss_pct, -0.05)
        self.assertEqual(candidate.take_profit_pct, 0.08)
        self.assertEqual(candidate.exit_rule, "close_below_ma5")

    def test_cup_handle_breakout_emits_candidate_with_handle_low_exit(self) -> None:
        leading = [10.0 + index * 0.03 for index in range(60)]
        pattern = [10.0, 11.0, 12.0, 13.0, 12.0, 11.0, 10.5, 11.0, 12.0, 12.8, 12.4, 12.2, 12.4, 13.1]
        bars = [bar(index, price) for index, price in enumerate(leading + pattern, start=1)]
        for item in bars[-4:]:
            object.__setattr__(item, "amount", 40_000_000.0)

        candidate = tail.score_strategy("cup_handle_breakout", "000001.SZ", bars)

        self.assertIsNotNone(candidate)
        assert candidate is not None
        self.assertEqual(candidate.stop_loss_pct, -0.06)
        self.assertEqual(candidate.take_profit_pct, 0.12)
        self.assertEqual(candidate.exit_rule, "close_below_handle_low")

    def test_tail_suite_is_registered_without_loading_tongdaxin(self) -> None:
        strategy = get_strategy("tail")

        self.assertEqual(strategy.strategy_id, "tail")
        with self.assertRaises(SystemExit) as raised:
            strategy.run(["--help"])
        self.assertEqual(raised.exception.code, 0)

    def test_kelly_and_exit_execution_apply_conservative_daily_ordering(self) -> None:
        config = BacktestConfig()
        position = Position(
            symbol="000001.SZ",
            strategy_id="steady_momentum",
            entry_date="2026-01-01",
            entry_price=10.0,
            quantity=100,
            stop_loss_pct=-0.04,
            take_profit_pct=0.08,
            max_holding_days=10,
            exit_rule="close_below_ma5",
        )
        gap_bar = DailyBar("2026-01-02", open=9.0, high=10.0, low=8.8, close=9.5, volume=1.0, amount=1.0)
        both_touched_bar = DailyBar("2026-01-02", open=10.0, high=11.0, low=9.0, close=10.5, volume=1.0, amount=1.0)

        self.assertAlmostEqual(kelly_fraction(0.6209, 1.81), 0.4114524862, places=8)
        self.assertEqual(execute_exit(position, gap_bar, config).reason, "gap_stop_loss")
        conservative = execute_exit(position, both_touched_bar, config)
        self.assertEqual(conservative.reason, "stop_loss")
        self.assertAlmostEqual(conservative.exit_price, 9.6 * (1.0 - config.sell_slippage))

    def test_report_outputs_include_trade_statistics_and_limitations(self) -> None:
        trades = [
            Trade("steady_momentum", "000001.SZ", "2026-01-01", "2026-01-02", 10.0, 11.0, 100, "take_profit", 1.0, 9.0, 0.1),
            Trade("steady_momentum", "000002.SZ", "2026-01-01", "2026-01-02", 10.0, 9.0, 100, "stop_loss", -1.0, -11.0, 0.1),
        ]
        payload = build_report_payload("steady_momentum", "2026-01-01", "2026-01-31", trades, [100_000.0, 101_000.0])

        self.assertEqual(payload["max_positions"], 3)
        self.assertEqual(payload["win_rate"], 0.5)
        self.assertEqual(payload["payoff_ratio"], 1.0)
        self.assertTrue(payload["source_performance_is_not_reproduction"])
        with tempfile.TemporaryDirectory() as temporary:
            output = write_backtest_outputs(Path(temporary), payload, trades)
            self.assertTrue((output / "trades.csv").is_file())
            self.assertTrue((output / "report.json").is_file())
            self.assertIn("日线先后顺序", (output / "report.md").read_text(encoding="utf-8"))

    def test_daily_replay_never_exceeds_three_positions_and_closes_a_stop(self) -> None:
        prices = [10.0 + index * 0.05 for index in range(70)] + [9.0, 8.8, 8.7]
        bars = [bar(index, price) for index, price in enumerate(prices, start=1)]

        result = run_backtest_from_bars(
            "steady_momentum",
            {"000001.SZ": bars, "000002.SZ": bars, "000003.SZ": bars, "000004.SZ": bars},
            None,
            BacktestConfig(),
        )

        self.assertLessEqual(result.max_open_positions, 3)
        self.assertTrue(result.trades)
        self.assertTrue({"stop_loss", "gap_stop_loss"} & {trade.reason for trade in result.trades})

    def test_backtest_help_requires_no_tongdaxin_connection(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            backtest_main(["--help"])
        self.assertEqual(raised.exception.code, 0)


if __name__ == "__main__":
    unittest.main()
