"""Compatibility import for the public market-data provider module."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.market_data.providers import *  # noqa: F403
