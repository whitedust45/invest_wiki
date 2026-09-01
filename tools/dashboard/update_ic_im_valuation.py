#!/usr/bin/env python3
"""Compatibility CLI for the public market-data valuation updater."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.market_data.valuation import *  # noqa: F403
from modules.market_data.valuation import main


if __name__ == "__main__":
    raise SystemExit(main())
