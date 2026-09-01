#!/usr/bin/env python3
"""Offline tests for dashboard market data providers."""

from __future__ import annotations

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.market_data import providers as market_data  # noqa: E402


class FakeHttpResponse:
    def __init__(self, payload: dict):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def read(self) -> bytes:
        return json.dumps(self.payload).encode("utf-8")


class FakeFrame:
    def to_dict(self, orient: str = "") -> list[dict[str, object]]:
        if orient != "records":
            raise AssertionError("SDK frame should be converted with records orientation")
        return [{"ts_code": "000001.SZ", "trade_date": "20260703", "close": 12.34}]


class FakeTushareSdkModule:
    def __init__(self):
        self.calls: list[tuple[str, dict[str, object]]] = []

    def pro_api(self, token: str):
        self.token = token
        return self

    def query(self, api_name: str, **params):
        self.calls.append((api_name, params))
        return FakeFrame()


class FakeTushareProvider:
    def __init__(self):
        self.quote_calls: list[str] = []
        self.history_calls: list[tuple[str, int, str]] = []

    def fetch_quote(self, symbol: str) -> dict[str, object]:
        self.quote_calls.append(symbol)
        return {
            "symbol": symbol,
            "name": "平安银行",
            "price": 12.34,
            "source": "Tushare日线",
            "trade_time": "20260703",
        }

    def fetch_daily_history(self, symbol: str, count: int, end_date: str = "") -> list[dict[str, object]]:
        self.history_calls.append((symbol, count, end_date))
        return [
            {
                "date": "2026-07-03",
                "open": 12.0,
                "close": 12.34,
                "high": 12.5,
                "low": 11.9,
                "volume": 1000.0,
                "source": "Tushare前复权日线",
            }
        ]


class FakeFallbackProvider:
    def __init__(self):
        self.us_calls: list[str] = []
        self.ashare_calls: list[str] = []

    def fetch_quote(self, symbol: str, usd_cny: float | None = None) -> dict[str, object]:
        if market_data.is_us_symbol(symbol):
            self.us_calls.append(symbol)
            return {
                "symbol": symbol.upper(),
                "name": symbol.upper(),
                "price": 888.0,
                "source": "Yahoo美股人民币折算",
                "trade_time": "2026-07-03",
            }
        self.ashare_calls.append(symbol)
        return {
            "symbol": symbol,
            "name": symbol,
            "price": 9.99,
            "source": "腾讯A股",
            "trade_time": "20260703150000",
        }


class MarketDataTests(unittest.TestCase):
    def test_load_sync_env_reads_services_sync_env_without_overwriting_existing_values(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            env_dir = root / "services" / "sync"
            env_dir.mkdir(parents=True)
            (env_dir / ".env").write_text("TUSHARE_TOKEN=file-token\nTUSHARE_RPM=123\n", encoding="utf-8")
            with patch.dict(os.environ, {"TUSHARE_TOKEN": "existing-token"}, clear=False):
                config = market_data.load_sync_env(root)
                self.assertEqual(config["TUSHARE_TOKEN"], "file-token")
                self.assertEqual(os.environ["TUSHARE_TOKEN"], "existing-token")
                self.assertEqual(os.environ["TUSHARE_RPM"], "123")

    def test_tushare_client_posts_official_payload_and_maps_fields_to_dict_rows(self) -> None:
        captured: dict[str, object] = {}

        def fake_urlopen(req, timeout=0):
            captured["url"] = req.full_url
            captured["body"] = json.loads(req.data.decode("utf-8"))
            return FakeHttpResponse(
                {
                    "code": 0,
                    "msg": "",
                    "data": {
                        "fields": ["ts_code", "trade_date", "close"],
                        "items": [["000001.SZ", "20260703", 12.34]],
                    },
                }
            )

        client = market_data.TushareProClient(token="secret-token", urlopen=fake_urlopen, rate_limiter=market_data.NoopRateLimiter())
        rows = client.query("daily", {"ts_code": "000001.SZ"}, "ts_code,trade_date,close")

        self.assertEqual(captured["url"], "https://api.tushare.pro")
        self.assertEqual(
            captured["body"],
            {
                "api_name": "daily",
                "token": "secret-token",
                "params": {"ts_code": "000001.SZ"},
                "fields": "ts_code,trade_date,close",
            },
        )
        self.assertEqual(rows, [{"ts_code": "000001.SZ", "trade_date": "20260703", "close": 12.34}])

    def test_optional_tushare_sdk_client_maps_dataframe_records_to_dict_rows(self) -> None:
        sdk = FakeTushareSdkModule()
        client = market_data.TushareSdkClient(token="secret-token", sdk_module=sdk, rate_limiter=market_data.NoopRateLimiter())

        rows = client.query("daily", {"ts_code": "000001.SZ"}, "ts_code,trade_date,close")

        self.assertEqual(sdk.token, "secret-token")
        self.assertEqual(sdk.calls, [("daily", {"ts_code": "000001.SZ", "fields": "ts_code,trade_date,close"})])
        self.assertEqual(rows, [{"ts_code": "000001.SZ", "trade_date": "20260703", "close": 12.34}])

    def test_composite_provider_prefers_tushare_for_ashares_and_yahoo_fallback_for_us_tickers(self) -> None:
        tushare = FakeTushareProvider()
        fallback = FakeFallbackProvider()
        provider = market_data.CompositeMarketDataProvider(tushare_provider=tushare, fallback_provider=fallback)

        ashare_quote = provider.fetch_quote("000001")
        us_quote = provider.fetch_quote("QQQ")

        self.assertEqual(ashare_quote["source"], "Tushare日线")
        self.assertEqual(tushare.quote_calls, ["000001"])
        self.assertEqual(fallback.ashare_calls, [])
        self.assertEqual(us_quote["source"], "Yahoo美股人民币折算")
        self.assertEqual(fallback.us_calls, ["QQQ"])

    def test_history_payload_preserves_existing_json_schema(self) -> None:
        provider = market_data.CompositeMarketDataProvider(
            tushare_provider=FakeTushareProvider(),
            fallback_provider=FakeFallbackProvider(),
        )

        payload = market_data.build_history_payload(["000001", "QQQ"], 260, "2026-07-03", provider=provider)

        self.assertIn("generated_at", payload)
        self.assertEqual(payload["source"]["adjustment"], "qfq")
        self.assertIn("histories", payload)
        self.assertIn("errors", payload)
        self.assertEqual(payload["histories"]["000001"][0]["close"], 12.34)
        self.assertNotIn("QQQ", payload["histories"])


if __name__ == "__main__":
    unittest.main()
