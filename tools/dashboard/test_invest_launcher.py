"""Tests for the project-level investment workspace launcher."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

import invest  # noqa: E402


class InvestLauncherTests(unittest.TestCase):
    def test_build_command_routes_each_domain_to_its_real_module(self) -> None:
        ledger = invest.build_command(["ledger", "--port", "9000"])
        quotes = invest.build_command(["market", "quotes", "000858"])
        valuation = invest.build_command(["market", "valuation"])
        short = invest.build_command(["short", "brick", "--help"])
        backtest = invest.build_command(
            [
                "short",
                "backtest-brick",
                "--start-date",
                "2026-01-01",
                "--end-date",
                "2026-06-30",
            ]
        )

        self.assertEqual(ledger[:2], [sys.executable, str(ROOT / "modules" / "ledger" / "local_service.py")])
        self.assertEqual(quotes[:2], [sys.executable, str(ROOT / "modules" / "market_data" / "quotes.py")])
        self.assertEqual(valuation[:2], [sys.executable, str(ROOT / "modules" / "market_data" / "valuation.py")])
        self.assertEqual(short[:2], [sys.executable, str(ROOT / "modules" / "short_term" / "run.py")])
        self.assertEqual(backtest[:2], [sys.executable, str(ROOT / "modules" / "short_term" / "backtest_brick.py")])

    def test_build_command_rejects_unknown_workflow(self) -> None:
        with self.assertRaises(ValueError):
            invest.build_command(["market", "unknown"])


if __name__ == "__main__":
    unittest.main()
