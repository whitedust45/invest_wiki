#!/usr/bin/env python3
"""Offline tests for extracting tracked market instruments from wiki pages."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.market_data import watchlist  # noqa: E402


class WikiMarketWatchlistTests(unittest.TestCase):
    def test_extracts_a_share_good_companies_from_entity_tags(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            entities = root / "knowledge" / "wiki" / "entities"
            portfolios = root / "knowledge" / "wiki" / "portfolios"
            entities.mkdir(parents=True)
            portfolios.mkdir(parents=True)
            (portfolios / "a-share-good-companies-list.md").write_text(
                "| 层级 | 公司 |\n|---|---|\n| 核心跟踪层 | [[china-merchants-bank]] |\n",
                encoding="utf-8",
            )
            (entities / "china-merchants-bank.md").write_text(
                "---\n"
                "id: china-merchants-bank\n"
                "title: 招商银行 / China Merchants Bank\n"
                "tags: [A股, 银行, 600036.SH]\n"
                "---\n",
                encoding="utf-8",
            )

            instruments = watchlist.load_wiki_tracked_instruments(root)

        self.assertEqual(len(instruments), 1)
        self.assertEqual(instruments[0]["symbol"], "600036")
        self.assertEqual(instruments[0]["ts_code"], "600036.SH")
        self.assertEqual(instruments[0]["track_scope"], "wiki_good_company")
        self.assertEqual(instruments[0]["source_wiki_id"], "china-merchants-bank")

    def test_extracts_high_dividend_watchlist_codes_and_skips_manual_markets_for_auto_sync(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            portfolios = root / "knowledge" / "wiki" / "portfolios"
            portfolios.mkdir(parents=True)
            (portfolios / "high-dividend-cashflow-watchlist.md").write_text(
                "| 分层 | 代码 | 名称 | 市场 | 现金流属性 | 当前用途 |\n"
                "|---|---|---|---|---|---|\n"
                "| 核心质量现金流 | 000568 | 泸州老窖 | A股 | 高端白酒 | 核心观察 |\n"
                "| 核心质量现金流 | HK:03968 | 招商银行 | 港股 | 同一公司 H 股 | 手工 |\n",
                encoding="utf-8",
            )

            instruments = watchlist.load_wiki_tracked_instruments(root)

        self.assertEqual([item["symbol"] for item in instruments], ["000568"])
        self.assertEqual(instruments[0]["track_scope"], "wiki_watchlist")


if __name__ == "__main__":
    unittest.main()
