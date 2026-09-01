#!/usr/bin/env python3
"""Offline tests for the market-data SQLite store."""

from __future__ import annotations

import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.market_data import store  # noqa: E402


class MarketDataStoreTests(unittest.TestCase):
    def connect(self) -> sqlite3.Connection:
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        return store.connect_market_data_db(Path(tmp.name) / "market-data.db")

    def test_schema_creates_market_data_tables_without_foreign_keys(self) -> None:
        conn = self.connect()
        tables = {
            row[0]
            for row in conn.execute(
                "SELECT name FROM sqlite_master WHERE type = 'table'"
            ).fetchall()
        }

        expected = {
            "tracked_instruments",
            "sync_runs",
            "quote_snapshots",
            "daily_bars",
            "daily_metrics",
            "adjustment_factors",
            "derived_indicators",
            "basis_snapshots",
            "source_events",
            "projection_exports",
        }
        self.assertTrue(expected.issubset(tables))
        for table in expected:
            self.assertEqual(conn.execute(f"PRAGMA foreign_key_list({table})").fetchall(), [])

    def test_upsert_quote_is_idempotent_and_counts_revisions(self) -> None:
        conn = self.connect()
        run_id = store.start_sync_run(conn, "quote", ["000001"])
        quote = {
            "symbol": "000001",
            "name": "平安银行",
            "price": 12.34,
            "source": "Tushare前复权日线",
            "trade_time": "2026-07-03",
        }

        store.upsert_quote_snapshot(conn, quote, run_id)
        store.upsert_quote_snapshot(conn, quote, run_id)
        store.upsert_quote_snapshot(conn, {**quote, "price": 12.35}, run_id)

        rows = conn.execute(
            "SELECT symbol, price, revision_count FROM quote_snapshots"
        ).fetchall()
        self.assertEqual(rows, [("000001", 12.35, 1)])

    def test_generic_quote_upsert_preserves_more_specific_wiki_track_scope(self) -> None:
        conn = self.connect()
        store.upsert_instrument(
            conn,
            "600036",
            {"symbol": "600036", "ts_code": "600036.SH", "name": "招商银行"},
            source="knowledge/wiki/entities/china-merchants-bank.md",
            track_scope="wiki_good_company",
        )
        run_id = store.start_sync_run(conn, "quote", ["600036"])

        store.upsert_quote_snapshot(
            conn,
            {
                "symbol": "600036",
                "name": "招商银行",
                "price": 39.5,
                "source": "Tushare前复权日线",
                "trade_time": "2026-07-03",
            },
            run_id,
        )

        row = conn.execute(
            "SELECT track_scope, source FROM tracked_instruments WHERE symbol = '600036'"
        ).fetchone()
        self.assertEqual(row, ("wiki_good_company", "knowledge/wiki/entities/china-merchants-bank.md"))

    def test_legacy_import_is_idempotent_and_exports_existing_json_contracts(self) -> None:
        conn = self.connect()
        run_id = store.start_sync_run(conn, "legacy_import", ["000001", "512890"])
        quotes_payload = {
            "generated_at": "2026-07-05T00:00:00+00:00",
            "source": {"name": "legacy"},
            "quotes": {
                "000001": {
                    "symbol": "000001",
                    "name": "平安银行",
                    "price": 12.34,
                    "source": "legacy_json",
                    "trade_time": "2026-07-03 15:00:00",
                }
            },
            "errors": {},
        }
        history_payload = {
            "generated_at": "2026-07-05T00:00:00+00:00",
            "source": {"name": "legacy"},
            "histories": {
                "000001": [
                    {
                        "date": "2026-07-03",
                        "open": 12.0,
                        "close": 12.34,
                        "high": 12.5,
                        "low": 11.9,
                        "volume": 1000,
                        "source": "legacy_json",
                    }
                ]
            },
            "errors": {},
        }

        store.import_legacy_payloads(conn, quotes_payload, history_payload, None, run_id)
        store.import_legacy_payloads(conn, quotes_payload, history_payload, None, run_id)

        self.assertEqual(conn.execute("SELECT COUNT(*) FROM quote_snapshots").fetchone()[0], 1)
        self.assertEqual(conn.execute("SELECT COUNT(*) FROM daily_bars").fetchone()[0], 1)

        quotes = store.export_position_quotes(conn, ["000001"])
        history = store.export_position_history(conn, ["000001"])

        self.assertEqual(set(quotes.keys()), {"generated_at", "source", "quotes", "errors"})
        self.assertEqual(quotes["quotes"]["000001"]["price"], 12.34)
        self.assertEqual(set(history.keys()), {"generated_at", "source", "histories", "errors"})
        self.assertEqual(history["histories"]["000001"][0]["close"], 12.34)

    def test_exports_prefer_tushare_over_legacy_and_tencent_sources(self) -> None:
        conn = self.connect()
        run_id = store.start_sync_run(conn, "history", ["000001"])
        for source, close in [
            ("腾讯日线", 10.0),
            ("legacy_json", 11.0),
            ("Tushare前复权日线", 12.0),
        ]:
            store.upsert_daily_bar(
                conn,
                "000001",
                {"date": "2026-07-03", "close": close, "source": source},
                run_id,
                adjustment="qfq",
            )

        history = store.export_position_history(conn, ["000001"])

        self.assertEqual(history["histories"]["000001"], [
            {"date": "2026-07-03", "close": 12.0, "source": "Tushare前复权日线"}
        ])

    def test_quote_projection_prefers_newer_quote_before_source_priority(self) -> None:
        conn = self.connect()
        run_id = store.start_sync_run(conn, "quote", ["000001"])
        store.upsert_quote_snapshot(
            conn,
            {
                "symbol": "000001",
                "name": "平安银行",
                "price": 12.0,
                "source": "Tushare前复权日线",
                "trade_time": "2026-07-02",
            },
            run_id,
        )
        store.upsert_quote_snapshot(
            conn,
            {
                "symbol": "000001",
                "name": "平安银行",
                "price": 12.5,
                "source": "腾讯A股",
                "trade_time": "2026-07-03",
            },
            run_id,
        )

        quotes = store.export_position_quotes(conn, ["000001"])

        self.assertEqual(quotes["quotes"]["000001"]["price"], 12.5)
        self.assertEqual(quotes["quotes"]["000001"]["source"], "腾讯A股")

    def test_valuation_import_stores_metrics_indicators_and_basis_projection(self) -> None:
        conn = self.connect()
        run_id = store.start_sync_run(conn, "legacy_import", ["000905"])
        valuation_payload = {
            "schema_version": 1,
            "generated_at": "2026-07-05T00:00:00+00:00",
            "source": {"current_valuation": "legacy"},
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
                        "source": "basis-source",
                        "roll_notice": {"contract": "IC2607", "level": "normal", "message": "ok"},
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
                        ],
                    },
                }
            },
        }

        store.import_legacy_payloads(conn, None, None, valuation_payload, run_id)
        exported = store.export_ic_im_valuation(conn)

        self.assertEqual(conn.execute("SELECT COUNT(*) FROM daily_metrics").fetchone()[0], 2)
        self.assertEqual(conn.execute("SELECT COUNT(*) FROM derived_indicators").fetchone()[0], 2)
        self.assertEqual(conn.execute("SELECT COUNT(*) FROM basis_snapshots").fetchone()[0], 1)
        self.assertEqual(set(exported.keys()), {"schema_version", "generated_at", "source", "strategy_rule", "trade_date", "indexes"})
        self.assertEqual(exported["indexes"]["IC"]["pe"], 31.02)
        self.assertEqual(exported["indexes"]["IC"]["pb"], 2.47)
        self.assertEqual(exported["indexes"]["IC"]["pb_percentile"], 70.0)
        self.assertEqual(exported["indexes"]["IC"]["basis"]["contracts"][0]["contract"], "IC2607")


if __name__ == "__main__":
    unittest.main()
