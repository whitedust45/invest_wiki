#!/usr/bin/env python3
"""Simulate a mobile ledger write through the sync API and verify Gitee updated.

This script intentionally calls the same sync service endpoint that a phone
browser would use after local input is saved. It does not read or print tokens.
"""

from __future__ import annotations

import argparse
import json
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = ROOT / "services" / "sync" / ".env"
DEFAULT_DOMAIN_PATH = "/api/sync/data/dashboardState"


def read_env(path: Path = ENV_FILE) -> dict[str, str]:
    config: dict[str, str] = {}
    if not path.exists():
        raise RuntimeError(f"missing env file: {path}")
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        config[key.strip()] = value.strip()
    return config


def decimal_to_cents(value: str) -> int:
    amount = Decimal(str(value))
    return int((amount * Decimal("100")).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def cents_to_wan(cents: int) -> float:
    return float((Decimal(cents) / Decimal("100") / Decimal("10000")).quantize(Decimal("0.000001")))


def cents_to_yuan(cents: int) -> str:
    return str((Decimal(cents) / Decimal("100")).quantize(Decimal("0.01")))


def request_json(method: str, url: str, app_key: str, payload: Any | None = None, timeout: int = 20) -> Any:
    data = None
    headers = {
        "User-Agent": "invest-wiki-mobile-sync-test",
        "X-App-Key": app_key,
    }
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "ignore")[:500]
        raise RuntimeError(f"HTTP {error.code}: {detail}") from error
    return json.loads(body) if body else {}


def normalize_base_url(raw: str) -> str:
    return raw.rstrip("/")


def dashboard_state_url(base_url: str) -> str:
    return normalize_base_url(base_url) + DEFAULT_DOMAIN_PATH


def find_entry(state: dict[str, Any], entry_id: str) -> dict[str, Any] | None:
    ledger = state.get("ledger") if isinstance(state, dict) else None
    entries = ledger.get("entries") if isinstance(ledger, dict) else None
    if not isinstance(entries, list):
        return None
    for entry in entries:
        if isinstance(entry, dict) and entry.get("id") == entry_id:
            return entry
    return None


def append_test_entry(state: dict[str, Any], amount_cents: int, note: str) -> tuple[dict[str, Any], dict[str, Any]]:
    next_state = json.loads(json.dumps(state, ensure_ascii=False))
    ledger = next_state.setdefault("ledger", {})
    entries = ledger.setdefault("entries", [])
    if not isinstance(entries, list):
        raise RuntimeError("remote dashboardState.ledger.entries is not a list")

    now = datetime.now(timezone.utc)
    entry_id = f"sync-mobile-test-{now.strftime('%Y%m%d%H%M%S')}-{secrets.token_hex(4)}"
    entry = {
        "id": entry_id,
        "module": "dividend",
        "date": now.date().isoformat(),
        "bucket": "现金",
        "action": "deposit",
        "symbol": "SYNC-TEST",
        "name": "手机模拟写入测试",
        # Current frontend compatibility: amount is still displayed as 万元.
        "amount": cents_to_wan(amount_cents),
        "amountCents": amount_cents,
        "fee": 0,
        "feeCents": 0,
        "quantity": "",
        "price": "",
        "note": note,
        "testMeta": {
            "source": "tools/dashboard/test_gitee_sync_write.py",
            "amountYuan": cents_to_yuan(amount_cents),
            "createdAt": now.isoformat(timespec="seconds"),
        },
    }
    entries.append(entry)
    next_state["exportedAt"] = now.isoformat(timespec="seconds")
    next_state["source"] = "mobile-sync-test"
    return next_state, entry


def remove_entry(state: dict[str, Any], entry_id: str) -> dict[str, Any]:
    next_state = json.loads(json.dumps(state, ensure_ascii=False))
    ledger = next_state.setdefault("ledger", {})
    entries = ledger.get("entries")
    if not isinstance(entries, list):
        raise RuntimeError("remote dashboardState.ledger.entries is not a list")
    ledger["entries"] = [entry for entry in entries if not (isinstance(entry, dict) and entry.get("id") == entry_id)]
    next_state["exportedAt"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    next_state["source"] = "mobile-sync-test-cleanup"
    return next_state


def load_remote_state(base_url: str, app_key: str) -> tuple[dict[str, Any], str | None]:
    response = request_json("GET", dashboard_state_url(base_url), app_key)
    if not response.get("ok"):
        raise RuntimeError(response.get("error") or "sync API returned ok=false")
    data = response.get("data")
    if not isinstance(data, dict):
        raise RuntimeError("sync API data is not an object")
    return data, response.get("sha")


def save_remote_state(base_url: str, app_key: str, state: dict[str, Any]) -> str | None:
    response = request_json("POST", dashboard_state_url(base_url), app_key, {"data": state})
    if not response.get("ok"):
        raise RuntimeError(response.get("error") or "sync API returned ok=false on save")
    return response.get("sha")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Simulate a mobile ledger write and verify Gitee sync.")
    parser.add_argument("--base-url", help="Sync service base URL. Defaults to SYNC_PUBLIC_BASE_URL or http://127.0.0.1:8775.")
    parser.add_argument("--amount-yuan", default="100.00", help="Frontend-style input amount in yuan; persisted as integer cents.")
    parser.add_argument("--note", default="自动同步测试，可删除", help="Ledger note for the simulated mobile entry.")
    parser.add_argument("--cleanup", action="store_true", help="Remove the test entry after verification and push cleanup to Gitee.")
    parser.add_argument("--poll-seconds", type=float, default=1.0, help="Seconds to wait before re-reading remote state.")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    env = read_env()
    app_key = env.get("APP_ACCESS_KEY", "")
    if not app_key:
        raise RuntimeError("APP_ACCESS_KEY is empty in services/sync/.env")
    base_url = args.base_url or env.get("SYNC_PUBLIC_BASE_URL") or "http://127.0.0.1:8775"
    amount_cents = decimal_to_cents(args.amount_yuan)
    if amount_cents <= 0:
        raise RuntimeError("--amount-yuan must be positive")

    before_state, before_sha = load_remote_state(base_url, app_key)
    next_state, entry = append_test_entry(before_state, amount_cents, args.note)
    after_save_sha = save_remote_state(base_url, app_key, next_state)
    time.sleep(max(0.0, args.poll_seconds))
    verified_state, verified_sha = load_remote_state(base_url, app_key)
    verified_entry = find_entry(verified_state, entry["id"])
    if not verified_entry:
        raise RuntimeError("remote verification failed: test entry not found after save")
    if before_sha and verified_sha == before_sha:
        raise RuntimeError("remote verification failed: remote sha did not change")

    print("SYNC_WRITE_OK")
    print(f"base_url={base_url}")
    print(f"entry_id={entry['id']}")
    print(f"amount_cents={entry['amountCents']}")
    print(f"amount_yuan={entry['testMeta']['amountYuan']}")
    print(f"frontend_amount_wan={entry['amount']}")
    print(f"sha_before={before_sha}")
    print(f"sha_after_save={after_save_sha}")
    print(f"sha_verified={verified_sha}")

    if args.cleanup:
        cleaned = remove_entry(verified_state, entry["id"])
        cleanup_sha = save_remote_state(base_url, app_key, cleaned)
        time.sleep(max(0.0, args.poll_seconds))
        cleanup_state, cleanup_verified_sha = load_remote_state(base_url, app_key)
        if find_entry(cleanup_state, entry["id"]):
            raise RuntimeError("cleanup verification failed: test entry still exists")
        print("SYNC_CLEANUP_OK")
        print(f"sha_cleanup={cleanup_sha}")
        print(f"sha_cleanup_verified={cleanup_verified_sha}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
