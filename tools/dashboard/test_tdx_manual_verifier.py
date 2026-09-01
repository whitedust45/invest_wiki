"""Tests for the Windows-only active TDX manual comparison helper."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.short_term.verify_brick import (  # noqa: E402
    render_comparison_markdown,
    resolve_manual_symbols,
)


class TdxManualVerifierTests(unittest.TestCase):
    def test_manual_codes_resolve_against_selected_sector_members(self) -> None:
        symbols, unresolved = resolve_manual_symbols(
            "000001\n600000.SH\n999999",
            {"000001.SZ": [], "600000.SH": []},
        )

        self.assertEqual(symbols, {"000001.SZ", "600000.SH"})
        self.assertEqual(unresolved, ["999999"])

    def test_manual_comparison_renders_markdown_instead_of_json(self) -> None:
        report = render_comparison_markdown(
            {
                "manual": ["000001.SZ"],
                "unresolved_manual_tokens": [],
                "manual_vs_native": {"shared": ["000001.SZ"], "manual_only": [], "engine_only": []},
                "manual_vs_python": {"shared": [], "manual_only": ["000001.SZ"], "engine_only": ["600000.SH"]},
            }
        )

        self.assertIn("# 通达信手工选股核对", report)
        self.assertIn("| 原生 ZHUAN | 000001.SZ | - | - |", report)
        self.assertIn("| Python 砖型图 | - | 000001.SZ | 600000.SH |", report)


if __name__ == "__main__":
    unittest.main()
