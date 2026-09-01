"""Static contracts for Windows-only TDX scheduled-runner assets."""

from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCHEDULES_DIR = ROOT / "modules" / "short_term" / "schedules"
RUNNER = SCHEDULES_DIR / "run_tdx_brick_selector.ps1"
INSTALLER = SCHEDULES_DIR / "install_tdx_brick_selector_task.ps1"


class TdxScheduledRunnerAssetTests(unittest.TestCase):
    def test_runner_has_start_wait_deadline_and_daily_output_contract(self) -> None:
        source = RUNNER.read_text(encoding="utf-8")

        for token in (
            "TdxW.exe",
            "Start-Process",
            "Start-Sleep",
            "14:55",
            "--preflight",
            "--engine",
            "both",
            "tdx-brick-selector",
            "reports",
            "logs",
            ".md",
        ):
            self.assertIn(token, source)

    def test_installer_registers_a_weekday_interactive_task_without_catch_up(self) -> None:
        source = INSTALLER.read_text(encoding="utf-8")

        for token in (
            "Register-ScheduledTask",
            "TDX Brick Selector 14:40",
            "Monday",
            "Friday",
            "14:40",
            "Interactive",
            "IgnoreNew",
            "StartWhenAvailable",
        ):
            self.assertIn(token, source)


if __name__ == "__main__":
    unittest.main()
