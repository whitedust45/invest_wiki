#!/usr/bin/env python3
"""Compatibility CLI for the public market-data quote updater."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.market_data.quotes import *  # noqa: F403
from modules.market_data.quotes import main


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
