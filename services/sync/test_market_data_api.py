#!/usr/bin/env python3
"""Offline tests for dashboard market-data sync API helpers."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.ledger import local_service as sync_service  # noqa: E402
from modules.market_data import api as market_api  # noqa: E402


class MarketDataApiTests(unittest.TestCase):
    def test_symbols_from_query_prefers_explicit_comma_separated_symbols(self) -> None:
        symbols = sync_service.symbols_from_query({"symbols": ["000001, QQQ, 512890"]})

        self.assertEqual(symbols, ["000001", "QQQ", "512890"])

    def test_position_quotes_command_writes_dashboard_quotes_json(self) -> None:
        cmd = sync_service.position_quotes_command(["000001", "QQQ"])

        self.assertEqual(cmd[0], sys.executable)
        self.assertEqual(Path(cmd[1]).name, "quotes.py")
        self.assertIn("000001", cmd)
        self.assertIn("QQQ", cmd)
        self.assertIn("--output", cmd)
        self.assertIn(str(sync_service.POSITION_QUOTES_JSON), cmd)

    def test_position_history_command_writes_dashboard_history_json(self) -> None:
        cmd = sync_service.position_history_command(["000001"], days=260, end_date="2026-07-03")

        self.assertIn("--history-days", cmd)
        self.assertIn("260", cmd)
        self.assertIn("--history-output", cmd)
        self.assertIn(str(sync_service.POSITION_HISTORY_JSON), cmd)
        self.assertIn("--end-date", cmd)
        self.assertIn("2026-07-03", cmd)

    def test_market_data_job_registry_runs_refresh_in_background(self) -> None:
        registry = sync_service.MarketDataJobRegistry(max_workers=1)
        task = Mock(return_value={"quotes": 2, "history_rows": 3})

        job = registry.submit("wiki_refresh", task, symbols=["000001", "600036"])
        initial = registry.get(job["id"])
        final = registry.wait(job["id"], timeout=5)
        registry.shutdown()

        self.assertEqual(initial["type"], "wiki_refresh")
        self.assertIn(initial["status"], {"queued", "running", "success"})
        self.assertEqual(final["status"], "success")
        self.assertEqual(final["result"], {"quotes": 2, "history_rows": 3})
        task.assert_called_once()

    def test_market_data_job_registry_prunes_old_finished_jobs(self) -> None:
        registry = sync_service.MarketDataJobRegistry(max_workers=1, max_jobs=1)
        task = Mock(return_value={"ok": True})

        first = registry.submit("wiki_refresh", task)
        registry.wait(first["id"], timeout=5)
        second = registry.submit("wiki_refresh", task)
        registry.wait(second["id"], timeout=5)
        registry.shutdown()

        self.assertIsNone(registry.get(first["id"]))
        self.assertEqual(registry.get(second["id"])["status"], "success")

    def test_market_data_refresh_command_uses_wiki_scope_without_blocking_subprocess_in_handler(self) -> None:
        symbols = ["000568", "600036"]
        cmd = sync_service.position_history_command(symbols, days=520)

        self.assertIn("000568", cmd)
        self.assertIn("600036", cmd)
        self.assertIn("--history-days", cmd)
        self.assertIn("520", cmd)

    def test_market_data_endpoints_require_app_key_only_when_configured(self) -> None:
        handler = object.__new__(sync_service.SyncHandler)
        handler.headers = {}
        sent: list[tuple[object, dict[str, object]]] = []
        handler.send_json = lambda status, payload: sent.append((status, payload))  # type: ignore[method-assign]

        handler.config = sync_service.SyncConfig("", "", "", "", "", "", "127.0.0.1", 8775)
        self.assertTrue(handler.require_app_key_if_configured())

        handler.config = sync_service.SyncConfig("", "", "", "", "", "secret-key", "127.0.0.1", 8775)
        self.assertFalse(handler.require_app_key_if_configured())
        self.assertEqual(sent[-1][1]["error"], "invalid app access key")

        handler.headers = {"X-App-Key": "secret-key"}
        self.assertTrue(handler.require_app_key_if_configured())

    def test_refresh_wiki_market_data_seeds_wiki_instrument_metadata_before_fetch(self) -> None:
        instruments = [
            {
                "symbol": "600036",
                "ts_code": "600036.SH",
                "name": "招商银行",
                "track_scope": "wiki_good_company",
                "source_wiki_path": "knowledge/wiki/entities/china-merchants-bank.md",
            }
        ]
        completed = sync_service.subprocess.CompletedProcess(args=[], returncode=0, stdout="", stderr="")
        with patch.object(market_api, "load_wiki_tracked_instruments", return_value=instruments), \
            patch.object(market_api, "seed_wiki_instruments") as seed, \
            patch.object(market_api.subprocess, "run", return_value=completed), \
            patch.object(market_api, "read_projection", side_effect=[
                {"quotes": {"600036": {"price": 39.5}}, "errors": {}},
                {"histories": {"600036": [{"close": 39.5}]}, "errors": {}},
            ]):
            result = market_api.refresh_wiki_market_data(days=520)

        seed.assert_called_once_with(instruments)
        self.assertEqual(result["symbols"], ["600036"])


if __name__ == "__main__":
    unittest.main()
