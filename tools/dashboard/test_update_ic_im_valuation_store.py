#!/usr/bin/env python3
"""Integration tests for update_ic_im_valuation SQLite projection."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.market_data import store  # noqa: E402
from modules.market_data import valuation as script  # noqa: E402


class UpdateIcImValuationStoreTests(unittest.TestCase):
    def test_main_writes_sqlite_store_and_exports_valuation_json_shape(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            db_path = root / "market-data.db"
            output = root / "ic-im-valuation.json"
            valuation_payload = {
                "schema_version": 1,
                "generated_at": "2026-07-05T00:00:00+00:00",
                "source": {"current_valuation": "test"},
                "strategy_rule": "PB percentile is the decision input.",
                "trade_date": "20260703",
                "indexes": {
                    "IC": {
                        "code": "000905",
                        "name": "中证500",
                        "underlying_future": "IC",
                        "trade_date": "20260703",
                        "pe": 31.02,
                        "pb": 2.47,
                        "pe_source": "csindex_pe_history",
                        "pb_source": "csindex_current",
                        "pe_percentile": 80.0,
                        "pb_percentile": 70.0,
                        "pb_percentile_source": "local_csv",
                        "pb_percentile_manual_required": False,
                        "history_window_years": 10,
                        "basis": {
                            "contracts": [
                                {
                                    "contract": "IC2607",
                                    "spot": 8745.26,
                                    "future": 8688.8,
                                    "basis": 56.46,
                                    "annualized_basis_pct": 19.76,
                                    "days_left": 12,
                                    "delivery_date": "2026-07-17",
                                    "roll_notice": {"level": "normal", "message": "ok"},
                                }
                            ]
                        },
                    }
                },
            }

            with patch.object(script, "build_payload", return_value=valuation_payload), \
                patch.object(script, "connect_market_data_db", side_effect=lambda: store.connect_market_data_db(db_path)):
                with patch.object(sys, "argv", ["update_ic_im_valuation.py", "--output", str(output), "--no-include-basis"]):
                    code = script.main()

            self.assertEqual(code, 0)
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(set(payload.keys()), {"schema_version", "generated_at", "source", "strategy_rule", "trade_date", "indexes"})
            self.assertEqual(payload["indexes"]["IC"]["pb_percentile"], 70.0)
            self.assertEqual(payload["indexes"]["IC"]["basis"]["contracts"][0]["contract"], "IC2607")
            with store.connect_market_data_db(db_path) as conn:
                self.assertEqual(conn.execute("SELECT COUNT(*) FROM daily_metrics").fetchone()[0], 2)
                self.assertEqual(conn.execute("SELECT COUNT(*) FROM derived_indicators").fetchone()[0], 2)
                self.assertEqual(conn.execute("SELECT COUNT(*) FROM basis_snapshots").fetchone()[0], 1)
                self.assertEqual(conn.execute("SELECT COUNT(*) FROM projection_exports").fetchone()[0], 1)


if __name__ == "__main__":
    unittest.main()
