#!/usr/bin/env python3
"""Offline tests for IC/IM valuation payload assembly."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.market_data import valuation  # noqa: E402


class IcImValuationTests(unittest.TestCase):
    def test_build_payload_prefers_tushare_index_dailybasic_for_current_pb_and_percentile(self) -> None:
        tushare_rows = {
            "IC": [
                {"trade_date": "20260701", "pe_ttm": 20.0, "pb": 1.0},
                {"trade_date": "20260703", "pe_ttm": 30.0, "pb": 1.5},
            ],
            "IM": [
                {"trade_date": "20260701", "pe_ttm": 40.0, "pb": 2.0},
                {"trade_date": "20260703", "pe_ttm": 50.0, "pb": 3.0},
            ],
        }
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(valuation, "fetch_tushare_dailybasic_by_index", return_value=tushare_rows), \
                patch.object(valuation, "latest_current_valuations", return_value=("20260703", {})), \
                patch.object(valuation, "pe_history", return_value=([], {})), \
                patch.object(valuation, "fetch_basis", return_value={}):
                payload = valuation.build_payload(Path(tmp), years=1, include_basis=False)

        ic = payload["indexes"]["IC"]
        im = payload["indexes"]["IM"]
        self.assertEqual(payload["trade_date"], "20260703")
        self.assertEqual(ic["pb"], 1.5)
        self.assertEqual(ic["pe"], 30.0)
        self.assertEqual(ic["pb_source"], "tushare_index_dailybasic")
        self.assertEqual(ic["pe_source"], "tushare_index_dailybasic")
        self.assertEqual(ic["pb_percentile"], 100.0)
        self.assertEqual(im["pb_percentile"], 100.0)
        self.assertFalse(ic["pb_percentile_manual_required"])

    def test_build_payload_falls_back_when_tushare_fields_are_missing(self) -> None:
        tushare_rows = {
            "IC": [{"trade_date": "20260703"}],
            "IM": [{"trade_date": "20260703"}],
        }
        current = {
            "中证500": {"tradeDate": "20260703", "pb": 1.2, "pe": 15.0},
            "中证1000": {"tradeDate": "20260703", "pb": 1.8, "pe": 25.0},
        }
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(valuation, "fetch_tushare_dailybasic_by_index", return_value=tushare_rows), \
                patch.object(valuation, "latest_current_valuations", return_value=("20260703", current)), \
                patch.object(valuation, "pe_history", return_value=([10.0, 20.0], {"peg": 20.0, "tradeDate": "20260703"})), \
                patch.object(valuation, "eastmoney_current_valuation", side_effect=AssertionError("eastmoney should not be called")), \
                patch.object(valuation, "fetch_basis", return_value={}):
                payload = valuation.build_payload(Path(tmp), years=1, include_basis=False)

        ic = payload["indexes"]["IC"]
        self.assertEqual(ic["pb"], 1.2)
        self.assertEqual(ic["pe"], 20.0)
        self.assertEqual(ic["pb_source"], "csindex_current")
        self.assertEqual(ic["pe_source"], "csindex_pe_history")


if __name__ == "__main__":
    unittest.main()
