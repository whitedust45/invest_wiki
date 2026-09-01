"""Architecture regression tests for the three business modules."""

from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class ModuleBoundaryTests(unittest.TestCase):
    def test_three_modules_have_explicit_public_entrypoints(self) -> None:
        expected = {
            "ledger": "local_service.py",
            "market_data": "api.py",
            "short_term": "registry.py",
        }
        for module, entrypoint in expected.items():
            self.assertTrue((ROOT / "modules" / module / entrypoint).is_file())

    def test_market_data_does_not_depend_on_ledger_or_short_term(self) -> None:
        source = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (ROOT / "modules" / "market_data").glob("*.py")
        )
        self.assertNotIn("modules.ledger", source)
        self.assertNotIn("modules.short_term", source)

    def test_short_term_registry_exposes_brick_as_a_plugin(self) -> None:
        from modules.short_term.registry import available_strategies

        self.assertEqual([strategy.strategy_id for strategy in available_strategies()], ["brick"])

    def test_dashboard_uses_the_current_local_service_port(self) -> None:
        source = (ROOT / "apps" / "dashboard" / "app.js").read_text(encoding="utf-8")

        self.assertIn("window.location.origin", source)
        self.assertNotIn('dashboardServiceBaseUrl = "http://127.0.0.1:8775', source)


if __name__ == "__main__":
    unittest.main()
