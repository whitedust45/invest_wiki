#!/usr/bin/env python3
"""Compatibility CLI for the short-term brick manual verifier."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.short_term.verify_brick import *  # noqa: F403
from modules.short_term.verify_brick import main


if __name__ == "__main__":
    raise SystemExit(main())
