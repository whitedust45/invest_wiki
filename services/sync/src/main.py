#!/usr/bin/env python3
"""Compatibility entrypoint for the ledger local service."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))

from modules.ledger.local_service import *  # noqa: F403
from modules.ledger.local_service import main


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
