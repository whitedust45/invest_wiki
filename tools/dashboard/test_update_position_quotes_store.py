#!/usr/bin/env python3
"""Integration tests for update_position_quotes SQLite projection."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.market_data import quotes as script  # noqa: E402
from modules.market_data import store  # noqa: E402


class UpdatePositionQuotesStoreTests(unittest.TestCase):
    def test_main_writes_sqlite_store_and_exports_existing_json_shapes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            db_path = root / "market-data.db"
            quotes_output = root / "position-quotes.json"
            history_output = root / "position-history.json"
            quotes_payload = {
                "generated_at": "2026-07-05T00:00:00+00:00",
                "source": {"name": "test"},
                "quotes": {
                    "000001": {
                        "symbol": "000001",
                        "name": "平安银行",
                        "price": 12.34,
                        "source": "Tushare前复权日线",
                        "trade_time": "2026-07-03",
                    }
                },
                "errors": {"600000": "permission denied"},
            }
            history_payload = {
                "generated_at": "2026-07-05T00:00:00+00:00",
                "source": {"name": "test"},
                "histories": {
                    "000001": [
                        {"date": "2026-07-03", "close": 12.34, "source": "Tushare前复权日线"}
                    ]
                },
                "errors": {"600000": "history unavailable"},
            }

            with patch.object(script, "build_market_quotes_payload", return_value=quotes_payload), \
                patch.object(script, "build_market_history_payload", return_value=history_payload), \
                patch.object(script, "connect_market_data_db", side_effect=lambda: store.connect_market_data_db(db_path)):
                code = script.main([
                    "000001",
                    "--output",
                    str(quotes_output),
                    "--history-days",
                    "1",
                    "--history-output",
                    str(history_output),
                ])

            self.assertEqual(code, 0)
            quotes = json.loads(quotes_output.read_text(encoding="utf-8"))
            history = json.loads(history_output.read_text(encoding="utf-8"))
            self.assertEqual(set(quotes.keys()), {"generated_at", "source", "quotes", "errors"})
            self.assertEqual(set(history.keys()), {"generated_at", "source", "histories", "errors"})
            self.assertEqual(quotes["quotes"]["000001"]["price"], 12.34)
            self.assertEqual(quotes["errors"], {"600000": "permission denied"})
            self.assertEqual(history["histories"]["000001"][0]["close"], 12.34)
            self.assertEqual(history["errors"], {"600000": "history unavailable"})

            with store.connect_market_data_db(db_path) as conn:
                self.assertEqual(conn.execute("SELECT COUNT(*) FROM quote_snapshots").fetchone()[0], 1)
                self.assertEqual(conn.execute("SELECT COUNT(*) FROM daily_bars").fetchone()[0], 1)
                self.assertEqual(conn.execute("SELECT COUNT(*) FROM source_events").fetchone()[0], 2)
                self.assertEqual(conn.execute("SELECT COUNT(*) FROM projection_exports").fetchone()[0], 2)


if __name__ == "__main__":
    unittest.main()
