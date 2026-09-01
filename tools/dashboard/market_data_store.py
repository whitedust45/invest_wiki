"""Compatibility import for the public market-data fact store."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.market_data.store import *  # noqa: F403
