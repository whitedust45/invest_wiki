#!/usr/bin/env python3
"""Serve the dashboard with local APIs for data that cannot be fetched by CORS."""

from __future__ import annotations

import argparse
import json
import sqlite3
import subprocess
import sys
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


ROOT = Path(__file__).resolve().parents[1]
VALUATION_JSON = ROOT / "hybrid-barbell-dashboard" / "data" / "ic-im-valuation.json"
LEDGER_DB = ROOT / "hybrid-barbell-dashboard" / "data" / "ledger.db"
UPDATE_IC_IM = ROOT / "scripts" / "update_ic_im_valuation.py"
MAX_LEDGER_BYTES = 10 * 1024 * 1024


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def connect_ledger_db() -> sqlite3.Connection:
    LEDGER_DB.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(LEDGER_DB)
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS snapshots (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            created_at TEXT NOT NULL,
            payload_json TEXT NOT NULL
        )
        """
    )
    return conn


def validate_ledger_payload(payload: object) -> dict:
    if not isinstance(payload, dict):
        raise ValueError("ledger payload must be an object")
    entries = payload.get("entries")
    settings = payload.get("settings")
    if entries is not None and not isinstance(entries, list):
        raise ValueError("ledger.entries must be a list")
    if settings is not None and not isinstance(settings, dict):
        raise ValueError("ledger.settings must be an object")
    return {
        "entries": entries if isinstance(entries, list) else [],
        "settings": settings if isinstance(settings, dict) else {},
    }


def write_ledger_snapshot(payload: dict) -> dict:
    created_at = utc_now()
    payload_json = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    with connect_ledger_db() as conn:
        cursor = conn.execute(
            "INSERT INTO snapshots (created_at, payload_json) VALUES (?, ?)",
            (created_at, payload_json),
        )
        snapshot_id = int(cursor.lastrowid)
    return {"id": snapshot_id, "created_at": created_at}


def read_latest_ledger_snapshot() -> dict | None:
    with connect_ledger_db() as conn:
        row = conn.execute(
            "SELECT id, created_at, payload_json FROM snapshots ORDER BY id DESC LIMIT 1"
        ).fetchone()
    return ledger_snapshot_from_row(row)


def read_ledger_snapshot(snapshot_id: int) -> dict | None:
    with connect_ledger_db() as conn:
        row = conn.execute(
            "SELECT id, created_at, payload_json FROM snapshots WHERE id = ?",
            (snapshot_id,),
        ).fetchone()
    return ledger_snapshot_from_row(row)


def ledger_snapshot_from_row(row: tuple | None) -> dict | None:
    if row is None:
        return None
    return {
        "id": int(row[0]),
        "created_at": row[1],
        "ledger": json.loads(row[2]),
    }


def list_ledger_snapshots(limit: int) -> list[dict]:
    with connect_ledger_db() as conn:
        rows = conn.execute(
            "SELECT id, created_at, payload_json FROM snapshots ORDER BY id DESC LIMIT ?",
            (limit,),
        ).fetchall()
    backups = []
    for row in rows:
        ledger = json.loads(row[2])
        entries = ledger.get("entries") if isinstance(ledger, dict) else []
        backups.append(
            {
                "id": int(row[0]),
                "created_at": row[1],
                "entries_count": len(entries) if isinstance(entries, list) else 0,
            }
        )
    return backups


class DashboardHandler(SimpleHTTPRequestHandler):
    def translate_path(self, path: str) -> str:
        self.directory = str(ROOT)
        return super().translate_path(path)

    def end_headers(self) -> None:
        self.send_header("Cache-Control", "no-store")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        super().end_headers()

    def do_GET(self) -> None:  # noqa: N802 - inherited API name
        path = urlsplit(self.path).path
        if path == "/api/ic-im-valuation":
            self.handle_ic_im_valuation()
            return
        if path == "/api/ledger":
            self.handle_get_ledger()
            return
        if path == "/api/ledger/backups":
            self.handle_list_ledger_backups()
            return
        super().do_GET()

    def do_POST(self) -> None:  # noqa: N802 - inherited API name
        if urlsplit(self.path).path == "/api/ledger":
            self.handle_post_ledger()
            return
        self.send_json(HTTPStatus.NOT_FOUND, {"ok": False, "error": "not found"})

    def do_OPTIONS(self) -> None:  # noqa: N802 - inherited API name
        self.send_response(HTTPStatus.NO_CONTENT)
        self.end_headers()

    def send_json(self, status: HTTPStatus, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def handle_ic_im_valuation(self) -> None:
        try:
            completed = subprocess.run(
                [sys.executable, str(UPDATE_IC_IM), "--output", str(VALUATION_JSON)],
                cwd=ROOT,
                check=False,
                capture_output=True,
                text=True,
                timeout=75,
            )
        except subprocess.TimeoutExpired:
            self.send_json(
                HTTPStatus.GATEWAY_TIMEOUT,
                {"ok": False, "error": "IC/IM valuation update timed out"},
            )
            return

        if completed.returncode != 0:
            self.send_json(
                HTTPStatus.BAD_GATEWAY,
                {
                    "ok": False,
                    "error": "IC/IM valuation update failed",
                    "stdout": completed.stdout[-2000:],
                    "stderr": completed.stderr[-2000:],
                },
            )
            return

        try:
            payload = json.loads(VALUATION_JSON.read_text(encoding="utf-8"))
        except OSError as error:
            self.send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"ok": False, "error": str(error)})
            return

        self.send_json(HTTPStatus.OK, payload)

    def read_json_body(self) -> object:
        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError as error:
            raise ValueError("invalid Content-Length") from error
        if content_length <= 0:
            raise ValueError("empty request body")
        if content_length > MAX_LEDGER_BYTES:
            raise OverflowError("ledger payload is too large")
        raw_body = self.rfile.read(content_length)
        return json.loads(raw_body.decode("utf-8"))

    def handle_get_ledger(self) -> None:
        query = parse_qs(urlsplit(self.path).query)
        try:
            snapshot_id = int(query["id"][0]) if "id" in query else None
        except (TypeError, ValueError):
            self.send_json(HTTPStatus.BAD_REQUEST, {"ok": False, "error": "invalid snapshot id"})
            return
        try:
            snapshot = read_ledger_snapshot(snapshot_id) if snapshot_id is not None else read_latest_ledger_snapshot()
        except (OSError, sqlite3.Error, json.JSONDecodeError) as error:
            self.send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"ok": False, "error": str(error)})
            return
        if snapshot is None:
            self.send_json(HTTPStatus.OK, {"ok": True, "empty": True})
            return
        self.send_json(HTTPStatus.OK, {"ok": True, "empty": False, "snapshot": snapshot})

    def handle_post_ledger(self) -> None:
        try:
            payload = validate_ledger_payload(self.read_json_body())
            snapshot = write_ledger_snapshot(payload)
        except OverflowError as error:
            self.send_json(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, {"ok": False, "error": str(error)})
            return
        except (ValueError, json.JSONDecodeError) as error:
            self.send_json(HTTPStatus.BAD_REQUEST, {"ok": False, "error": str(error)})
            return
        except (OSError, sqlite3.Error) as error:
            self.send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"ok": False, "error": str(error)})
            return
        self.send_json(HTTPStatus.OK, {"ok": True, "snapshot": snapshot})

    def handle_list_ledger_backups(self) -> None:
        query = parse_qs(urlsplit(self.path).query)
        try:
            limit = int(query.get("limit", ["20"])[0])
        except ValueError:
            limit = 20
        limit = max(1, min(limit, 100))
        try:
            backups = list_ledger_snapshots(limit)
        except (OSError, sqlite3.Error, json.JSONDecodeError) as error:
            self.send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"ok": False, "error": str(error)})
            return
        self.send_json(HTTPStatus.OK, {"ok": True, "backups": backups})


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Serve hybrid barbell dashboard with local APIs.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8775)
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    server = ThreadingHTTPServer((args.host, args.port), DashboardHandler)
    print(f"Serving dashboard on http://{args.host}:{args.port}/hybrid-barbell-dashboard/index.html")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping dashboard server")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
