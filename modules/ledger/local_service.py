#!/usr/bin/env python3
"""Unified local sync service for the dashboard and Gitee-backed JSON data."""

from __future__ import annotations

import argparse
import base64
import concurrent.futures
import json
import sqlite3
import subprocess
import sys
import threading
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))


ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = ROOT / "services" / "sync" / ".env"
DASHBOARD_DIR = ROOT / "apps" / "dashboard"
VALUATION_JSON = DASHBOARD_DIR / "data" / "ic-im-valuation.json"
POSITION_QUOTES_JSON = DASHBOARD_DIR / "data" / "position-quotes.json"
POSITION_HISTORY_JSON = DASHBOARD_DIR / "data" / "position-history.json"
LEDGER_DB = DASHBOARD_DIR / "data" / "ledger.db"
UPDATE_IC_IM = ROOT / "modules" / "market_data" / "valuation.py"
UPDATE_POSITION_QUOTES = ROOT / "modules" / "market_data" / "quotes.py"
MAX_JSON_BYTES = 10 * 1024 * 1024
DEFAULT_POSITION_SYMBOLS = [
    "000858",
    "000568",
    "600887",
    "600153",
    "600036",
    "002818",
    "002091",
    "601668",
    "600177",
    "600873",
    "601318",
    "600938",
    "600941",
    "601225",
    "000651",
    "600690",
    "512890",
    "520890",
    "159569",
    "159545",
    "513630",
    "159117",
    "QQQ",
    "QLD",
    "SPY",
]

DATA_FILES = {
    "ledger": "ledger/current.json",
    "dashboardState": "dashboard/state.json",
    "valuation": "valuation/ic-im.json",
    "positionQuotes": "positions/quotes.json",
    "positionHistory": "positions/history.json",
    "syncMeta": "meta/sync-meta.json",
}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def read_env(path: Path = ENV_FILE) -> dict[str, str]:
    config: dict[str, str] = {}
    if not path.exists():
        return config
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        config[key.strip()] = value.strip()
    return config


def symbols_from_query(query: dict[str, list[str]], default: list[str] | None = None) -> list[str]:
    raw_values: list[str] = []
    raw_values.extend(query.get("symbols", []))
    raw_values.extend(query.get("symbol", []))
    symbols: list[str] = []
    for raw in raw_values:
        for part in str(raw).split(","):
            symbol = part.strip()
            if symbol and symbol not in symbols:
                symbols.append(symbol)
    return symbols or list(default or DEFAULT_POSITION_SYMBOLS)


def bounded_int_from_query(query: dict[str, list[str]], key: str, default: int, minimum: int, maximum: int) -> int:
    try:
        value = int(query.get(key, [str(default)])[0])
    except (TypeError, ValueError):
        value = default
    return max(minimum, min(maximum, value))


def position_quotes_command(symbols: list[str]) -> list[str]:
    return [
        sys.executable,
        str(UPDATE_POSITION_QUOTES),
        *symbols,
        "--output",
        str(POSITION_QUOTES_JSON),
    ]


def position_history_command(symbols: list[str], days: int, end_date: str = "") -> list[str]:
    command = [
        *position_quotes_command(symbols),
        "--history-days",
        str(days),
        "--history-output",
        str(POSITION_HISTORY_JSON),
    ]
    if end_date:
        command.extend(["--end-date", end_date])
    return command


def load_wiki_tracked_instruments() -> list[dict[str, Any]]:
    from modules.market_data.watchlist import load_wiki_tracked_instruments as load_items  # noqa: PLC0415

    return load_items(ROOT)


def load_wiki_tracked_symbols() -> list[str]:
    return [item["symbol"] for item in load_wiki_tracked_instruments()]


def seed_wiki_market_instruments(instruments: list[dict[str, Any]]) -> None:
    if not instruments:
        return
    from modules.market_data.store import connect_market_data_db, upsert_instrument  # noqa: PLC0415

    with connect_market_data_db() as conn:
        for item in instruments:
            symbol = str(item.get("symbol") or "").strip()
            if not symbol:
                continue
            upsert_instrument(
                conn,
                symbol,
                item,
                source=str(item.get("source_wiki_path") or item.get("source_wiki_id") or "wiki"),
                track_scope=str(item.get("track_scope") or "wiki_watchlist"),
            )


def refresh_wiki_market_data(days: int = 520, end_date: str = "") -> dict[str, Any]:
    instruments = load_wiki_tracked_instruments()
    symbols = [item["symbol"] for item in instruments]
    if not symbols:
        return {"symbols": [], "quotes": 0, "history_rows": 0, "errors": {"watchlist": "no wiki instruments found"}}
    seed_wiki_market_instruments(instruments)
    completed = subprocess.run(
        position_history_command(symbols, days=days, end_date=end_date),
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
        timeout=600,
    )
    quotes_payload = read_json_file(POSITION_QUOTES_JSON, {"quotes": {}, "errors": {}})
    history_payload = read_json_file(POSITION_HISTORY_JSON, {"histories": {}, "errors": {}})
    result = {
        "symbols": symbols,
        "returncode": completed.returncode,
        "quotes": len(quotes_payload.get("quotes") or {}),
        "quote_errors": quotes_payload.get("errors") or {},
        "history_rows": sum(len(rows) for rows in (history_payload.get("histories") or {}).values()),
        "history_errors": history_payload.get("errors") or {},
        "stdout": completed.stdout[-2000:],
        "stderr": completed.stderr[-2000:],
    }
    if completed.returncode != 0:
        raise RuntimeError(json.dumps(result, ensure_ascii=False))
    return result


class MarketDataJobRegistry:
    def __init__(self, max_workers: int = 2, max_jobs: int = 200):
        self.executor = concurrent.futures.ThreadPoolExecutor(max_workers=max_workers, thread_name_prefix="market-data")
        self.lock = threading.Lock()
        self.jobs: dict[str, dict[str, Any]] = {}
        self.max_jobs = max(1, int(max_jobs))

    def submit(self, job_type: str, func: Any, **params: Any) -> dict[str, Any]:
        job_id = f"{job_type}-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S')}-{uuid.uuid4().hex[:12]}"
        job = {
            "id": job_id,
            "type": job_type,
            "status": "queued",
            "created_at": utc_now(),
            "started_at": None,
            "finished_at": None,
            "params": params,
            "result": None,
            "error": None,
        }
        with self.lock:
            self.jobs[job_id] = job
            self._prune_finished_jobs_locked()

        def run_job() -> Any:
            with self.lock:
                self.jobs[job_id] = {**self.jobs[job_id], "status": "running", "started_at": utc_now()}
            try:
                result = func(**params)
            except Exception as error:  # noqa: BLE001 - job errors are reported through status API
                with self.lock:
                    self.jobs[job_id] = {
                        **self.jobs[job_id],
                        "status": "failed",
                        "finished_at": utc_now(),
                        "error": str(error),
                    }
                    self._prune_finished_jobs_locked()
                raise
            with self.lock:
                self.jobs[job_id] = {
                    **self.jobs[job_id],
                    "status": "success",
                    "finished_at": utc_now(),
                    "result": result,
                }
                self._prune_finished_jobs_locked()
            return result

        future = self.executor.submit(run_job)
        with self.lock:
            self.jobs[job_id]["future"] = future
        return self.public_job(job_id)

    def _prune_finished_jobs_locked(self) -> None:
        if len(self.jobs) <= self.max_jobs:
            return
        removable = len(self.jobs) - self.max_jobs
        for old_job_id, old_job in list(self.jobs.items()):
            if removable <= 0:
                break
            if old_job.get("status") in {"success", "failed"}:
                self.jobs.pop(old_job_id, None)
                removable -= 1

    def public_job(self, job_id: str) -> dict[str, Any]:
        with self.lock:
            job = dict(self.jobs[job_id])
        job.pop("future", None)
        return job

    def get(self, job_id: str) -> dict[str, Any] | None:
        with self.lock:
            if job_id not in self.jobs:
                return None
        return self.public_job(job_id)

    def wait(self, job_id: str, timeout: float | None = None) -> dict[str, Any]:
        with self.lock:
            future = self.jobs[job_id].get("future")
        if future is not None:
            try:
                future.result(timeout=timeout)
            except Exception:
                pass
        return self.public_job(job_id)

    def shutdown(self) -> None:
        self.executor.shutdown(wait=True)


# The ledger is a consumer of the public market-data facade. These aliases keep
# the HTTP composition root small while legacy implementation removal proceeds.
from modules.market_data import api as market_api  # noqa: E402

symbols_from_query = market_api.symbols_from_query
position_quotes_command = market_api.position_quotes_command
position_history_command = market_api.position_history_command
load_wiki_tracked_instruments = market_api.load_wiki_tracked_instruments
seed_wiki_market_instruments = market_api.seed_wiki_instruments
refresh_wiki_market_data = market_api.refresh_wiki_market_data
MarketDataJobRegistry = market_api.MarketDataJobRegistry


@dataclass(frozen=True)
class SyncConfig:
    api_base: str
    owner: str
    repo: str
    branch: str
    token: str
    app_access_key: str
    host: str
    port: int

    @classmethod
    def load(cls) -> "SyncConfig":
        env = read_env()
        required = ["GITEE_API_BASE", "GITEE_OWNER", "GITEE_REPO", "GITEE_BRANCH", "GITEE_TOKEN"]
        missing = [key for key in required if not env.get(key)]
        if missing:
            raise RuntimeError(f"missing required sync config: {', '.join(missing)}")
        return cls(
            api_base=env.get("GITEE_API_BASE", "https://gitee.com/api/v5").rstrip("/"),
            owner=env["GITEE_OWNER"],
            repo=env["GITEE_REPO"],
            branch=env.get("GITEE_BRANCH", "main"),
            token=env["GITEE_TOKEN"],
            app_access_key=env.get("APP_ACCESS_KEY", ""),
            host=env.get("SYNC_HOST", "127.0.0.1"),
            port=int(env.get("SYNC_PORT", "8775")),
        )


class GiteeError(RuntimeError):
    def __init__(self, status: int | str, message: str):
        super().__init__(f"Gitee API error {status}: {message}")
        self.status = status
        self.message = message


class GiteeClient:
    def __init__(self, config: SyncConfig):
        self.config = config

    def _repo_path(self, suffix: str) -> str:
        owner = urllib.parse.quote(self.config.owner, safe="")
        repo = urllib.parse.quote(self.config.repo, safe="")
        return f"/repos/{owner}/{repo}{suffix}"

    def _request(self, method: str, path: str, params: dict[str, Any] | None = None, body: dict[str, Any] | None = None) -> Any:
        query = params.copy() if params else {}
        if method == "GET":
            query["access_token"] = self.config.token
        url = f"{self.config.api_base}{path}"
        if query:
            url = f"{url}?{urllib.parse.urlencode(query)}"
        data = None
        headers = {"User-Agent": "invest-wiki-sync-service"}
        if body is not None:
            payload = body.copy()
            payload["access_token"] = self.config.token
            data = urllib.parse.urlencode(payload).encode("utf-8")
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                raw = response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", "ignore")
            raise GiteeError(error.code, detail.replace(self.config.token, "[redacted]")) from error
        except urllib.error.URLError as error:
            raise GiteeError("URL_ERROR", str(error.reason)) from error
        return json.loads(raw) if raw else {}

    def repo_info(self) -> dict[str, Any]:
        return self._request("GET", self._repo_path(""))

    def branch_info(self) -> dict[str, Any] | None:
        branch = urllib.parse.quote(self.config.branch, safe="")
        try:
            return self._request("GET", self._repo_path(f"/branches/{branch}"))
        except GiteeError as error:
            if error.status == 404:
                return None
            raise

    def get_file(self, path: str) -> dict[str, Any] | None:
        try:
            return self._request(
                "GET",
                self._repo_path(f"/contents/{urllib.parse.quote(path, safe='/')}"),
                {"ref": self.config.branch},
            )
        except GiteeError as error:
            if error.status == 404:
                return None
            raise

    def get_json(self, path: str) -> tuple[Any, dict[str, Any] | None]:
        info = self.get_file(path)
        if info is None:
            return None, None
        content = info.get("content") or ""
        try:
            decoded = base64.b64decode(content).decode("utf-8")
        except Exception as error:  # noqa: BLE001 - convert remote format errors into API errors
            raise GiteeError("DECODE_ERROR", str(error)) from error
        return json.loads(decoded), info

    def put_json(self, path: str, payload: Any, message: str) -> dict[str, Any]:
        text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
        encoded = base64.b64encode(text.encode("utf-8")).decode("ascii")
        existing = self.get_file(path)
        body = {
            "content": encoded,
            "message": message,
            "branch": self.config.branch,
        }
        if existing and existing.get("sha"):
            body["sha"] = existing["sha"]
            return self._request("PUT", self._repo_path(f"/contents/{urllib.parse.quote(path, safe='/')}"), body=body)
        try:
            return self._request("POST", self._repo_path(f"/contents/{urllib.parse.quote(path, safe='/')}"), body=body)
        except GiteeError as error:
            if error.status in (400, 404) and "Branch does not exist" in error.message:
                fallback = body.copy()
                fallback.pop("branch", None)
                return self._request("POST", self._repo_path(f"/contents/{urllib.parse.quote(path, safe='/')}"), body=fallback)
            raise


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


def ledger_snapshot_from_row(row: tuple | None) -> dict[str, Any] | None:
    if row is None:
        return None
    return {"id": int(row[0]), "created_at": row[1], "ledger": json.loads(row[2])}


def read_latest_ledger_snapshot() -> dict[str, Any] | None:
    with connect_ledger_db() as conn:
        row = conn.execute("SELECT id, created_at, payload_json FROM snapshots ORDER BY id DESC LIMIT 1").fetchone()
    return ledger_snapshot_from_row(row)


def read_ledger_snapshot(snapshot_id: int) -> dict[str, Any] | None:
    with connect_ledger_db() as conn:
        row = conn.execute(
            "SELECT id, created_at, payload_json FROM snapshots WHERE id = ?",
            (snapshot_id,),
        ).fetchone()
    return ledger_snapshot_from_row(row)


def list_ledger_snapshots(limit: int) -> list[dict[str, Any]]:
    with connect_ledger_db() as conn:
        rows = conn.execute(
            "SELECT id, created_at, payload_json FROM snapshots ORDER BY id DESC LIMIT ?",
            (limit,),
        ).fetchall()
    backups = []
    for row in rows:
        ledger = json.loads(row[2])
        entries = ledger.get("entries") if isinstance(ledger, dict) else []
        backups.append({"id": int(row[0]), "created_at": row[1], "entries_count": len(entries) if isinstance(entries, list) else 0})
    return backups


def validate_ledger_payload(payload: object) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise ValueError("ledger payload must be an object")
    entries = payload.get("entries")
    settings = payload.get("settings")
    if entries is not None and not isinstance(entries, list):
        raise ValueError("ledger.entries must be a list")
    if settings is not None and not isinstance(settings, dict):
        raise ValueError("ledger.settings must be an object")
    return {"entries": entries if isinstance(entries, list) else [], "settings": settings if isinstance(settings, dict) else {}}


def write_ledger_snapshot(payload: dict[str, Any]) -> dict[str, Any]:
    created_at = utc_now()
    payload_json = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    with connect_ledger_db() as conn:
        cursor = conn.execute(
            "INSERT INTO snapshots (created_at, payload_json) VALUES (?, ?)",
            (created_at, payload_json),
        )
        snapshot_id = int(cursor.lastrowid)
    return {"id": snapshot_id, "created_at": created_at}


def read_json_file(path: Path, default: Any) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return default


def bootstrap_payloads() -> dict[str, Any]:
    latest_ledger = read_latest_ledger_snapshot()
    initialized_at = utc_now()
    ledger = latest_ledger["ledger"] if latest_ledger else {"entries": [], "settings": {}}
    valuation = read_json_file(VALUATION_JSON, None)
    return {
        "ledger": ledger,
        "dashboardState": {
            "schemaVersion": 1,
            "exportedAt": initialized_at,
            "source": "services/sync:init-gitee",
            "ledger": ledger,
            "positionValuations": {},
            "history": [],
            "valuation": valuation,
            "historyView": "day",
        },
        "valuation": valuation,
        "positionQuotes": read_json_file(POSITION_QUOTES_JSON, {"quotes": {}, "errors": []}),
        "positionHistory": read_json_file(POSITION_HISTORY_JSON, {"histories": {}, "errors": []}),
        "syncMeta": {
            "schemaVersion": 1,
            "initializedAt": initialized_at,
            "updatedAt": initialized_at,
            "source": "services/sync:init-gitee",
            "dataFiles": DATA_FILES,
            "latestLocalLedgerSnapshot": {
                "id": latest_ledger["id"],
                "created_at": latest_ledger["created_at"],
            }
            if latest_ledger
            else None,
        },
    }


def init_gitee_data(client: GiteeClient) -> dict[str, Any]:
    payloads = bootstrap_payloads()
    results: dict[str, Any] = {}
    for domain, path in DATA_FILES.items():
        result = client.put_json(path, payloads[domain], f"init data file: {path}")
        results[domain] = {
            "path": path,
            "sha": (result.get("content") or {}).get("sha"),
        }
    return results


class SyncHandler(SimpleHTTPRequestHandler):
    config: SyncConfig
    market_jobs = MarketDataJobRegistry(max_workers=2)

    def translate_path(self, path: str) -> str:
        self.directory = str(ROOT)
        return super().translate_path(path)

    def end_headers(self) -> None:
        self.send_header("Cache-Control", "no-store")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-App-Key")
        super().end_headers()

    def do_OPTIONS(self) -> None:  # noqa: N802
        self.send_response(HTTPStatus.NO_CONTENT)
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802
        path = urllib.parse.urlsplit(self.path).path
        if path == "/api/market-data/watchlist":
            self.handle_market_data_watchlist()
            return
        if path.startswith("/api/market-data/jobs/"):
            self.handle_market_data_job_get(path.removeprefix("/api/market-data/jobs/"))
            return
        if path == "/api/ic-im-valuation":
            self.handle_ic_im_valuation()
            return
        if path == "/api/position-quotes":
            self.handle_position_quotes()
            return
        if path == "/api/position-history":
            self.handle_position_history()
            return
        if path == "/api/ledger":
            self.handle_get_ledger()
            return
        if path == "/api/ledger/backups":
            self.handle_list_ledger_backups()
            return
        if path == "/api/sync/health":
            self.handle_sync_health()
            return
        if path == "/api/sync/status":
            self.handle_sync_status()
            return
        if path == "/api/sync/data":
            self.handle_sync_data()
            return
        if path.startswith("/api/sync/data/"):
            self.handle_sync_domain_get(path.removeprefix("/api/sync/data/"))
            return
        super().do_GET()

    def do_POST(self) -> None:  # noqa: N802
        path = urllib.parse.urlsplit(self.path).path
        if path == "/api/market-data/refresh-wiki":
            self.handle_market_data_refresh_wiki()
            return
        if path == "/api/ledger":
            self.handle_post_ledger()
            return
        if path.startswith("/api/sync/data/"):
            self.handle_sync_domain_save(path.removeprefix("/api/sync/data/"))
            return
        self.send_json(HTTPStatus.NOT_FOUND, {"ok": False, "error": "not found"})

    def do_PUT(self) -> None:  # noqa: N802
        self.do_POST()

    def read_json_body(self) -> Any:
        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError as error:
            raise ValueError("invalid Content-Length") from error
        if content_length <= 0:
            raise ValueError("empty request body")
        if content_length > MAX_JSON_BYTES:
            raise OverflowError("request body is too large")
        raw_body = self.rfile.read(content_length)
        return json.loads(raw_body.decode("utf-8"))

    def send_json(self, status: HTTPStatus, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def client(self) -> GiteeClient:
        return GiteeClient(self.config)

    def app_key_valid(self) -> bool:
        expected = self.config.app_access_key
        if not expected:
            return False
        auth = self.headers.get("Authorization", "")
        if auth == f"Bearer {expected}":
            return True
        return self.headers.get("X-App-Key", "") == expected

    def require_app_key(self) -> bool:
        if self.app_key_valid():
            return True
        self.send_json(HTTPStatus.UNAUTHORIZED, {"ok": False, "error": "invalid app access key"})
        return False

    def require_app_key_if_configured(self) -> bool:
        if not self.config.app_access_key:
            return True
        return self.require_app_key()

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
            self.send_json(HTTPStatus.GATEWAY_TIMEOUT, {"ok": False, "error": "IC/IM valuation update timed out"})
            return
        if completed.returncode != 0:
            self.send_json(
                HTTPStatus.BAD_GATEWAY,
                {"ok": False, "error": "IC/IM valuation update failed", "stdout": completed.stdout[-2000:], "stderr": completed.stderr[-2000:]},
            )
            return
        self.send_json(HTTPStatus.OK, read_json_file(VALUATION_JSON, {"ok": False, "error": "valuation json missing"}))

    def handle_market_data_watchlist(self) -> None:
        if not self.require_app_key_if_configured():
            return
        try:
            instruments = load_wiki_tracked_instruments()
        except Exception as error:  # noqa: BLE001 - return parse errors to local caller
            self.send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"ok": False, "error": str(error)})
            return
        self.send_json(HTTPStatus.OK, {"ok": True, "count": len(instruments), "instruments": instruments})

    def handle_market_data_refresh_wiki(self) -> None:
        if not self.require_app_key_if_configured():
            return
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
        days = bounded_int_from_query(query, "days", 520, 5, 2500)
        end_date = (query.get("end_date") or query.get("endDate") or [""])[0]
        job = self.market_jobs.submit("wiki_market_refresh", refresh_wiki_market_data, days=days, end_date=end_date)
        self.send_json(HTTPStatus.ACCEPTED, {"ok": True, "job": job})

    def handle_market_data_job_get(self, job_id: str) -> None:
        if not self.require_app_key_if_configured():
            return
        job = self.market_jobs.get(job_id)
        if not job:
            self.send_json(HTTPStatus.NOT_FOUND, {"ok": False, "error": "job not found"})
            return
        self.send_json(HTTPStatus.OK, {"ok": True, "job": job})

    def handle_position_quotes(self) -> None:
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
        symbols = symbols_from_query(query)
        try:
            completed = subprocess.run(
                position_quotes_command(symbols),
                cwd=ROOT,
                check=False,
                capture_output=True,
                text=True,
                timeout=120,
            )
        except subprocess.TimeoutExpired:
            self.send_json(HTTPStatus.GATEWAY_TIMEOUT, {"ok": False, "error": "position quotes update timed out"})
            return
        if completed.returncode != 0:
            self.send_json(
                HTTPStatus.BAD_GATEWAY,
                {
                    "ok": False,
                    "error": "position quotes update failed",
                    "stdout": completed.stdout[-2000:],
                    "stderr": completed.stderr[-2000:],
                },
            )
            return
        self.send_json(HTTPStatus.OK, read_json_file(POSITION_QUOTES_JSON, {"ok": False, "error": "position quotes json missing"}))

    def handle_position_history(self) -> None:
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
        symbols = symbols_from_query(query)
        days = bounded_int_from_query(query, "days", 520, 5, 2500)
        end_date = (query.get("end_date") or query.get("endDate") or [""])[0]
        try:
            completed = subprocess.run(
                position_history_command(symbols, days=days, end_date=end_date),
                cwd=ROOT,
                check=False,
                capture_output=True,
                text=True,
                timeout=180,
            )
        except subprocess.TimeoutExpired:
            self.send_json(HTTPStatus.GATEWAY_TIMEOUT, {"ok": False, "error": "position history update timed out"})
            return
        if completed.returncode != 0:
            self.send_json(
                HTTPStatus.BAD_GATEWAY,
                {
                    "ok": False,
                    "error": "position history update failed",
                    "stdout": completed.stdout[-2000:],
                    "stderr": completed.stderr[-2000:],
                },
            )
            return
        self.send_json(HTTPStatus.OK, read_json_file(POSITION_HISTORY_JSON, {"ok": False, "error": "position history json missing"}))

    def handle_get_ledger(self) -> None:
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
        try:
            snapshot_id = int(query["id"][0]) if "id" in query else None
            snapshot = read_ledger_snapshot(snapshot_id) if snapshot_id is not None else read_latest_ledger_snapshot()
        except (OSError, sqlite3.Error, json.JSONDecodeError, ValueError) as error:
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
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
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

    def handle_sync_health(self) -> None:
        self.send_json(
            HTTPStatus.OK,
            {
                "ok": True,
                "service": "invest-wiki-sync",
                "time": utc_now(),
                "gitee": {"owner": self.config.owner, "repo": self.config.repo, "branch": self.config.branch, "token_configured": bool(self.config.token)},
                "app_access_key_configured": bool(self.config.app_access_key),
            },
        )

    def handle_sync_status(self) -> None:
        try:
            repo = self.client().repo_info()
            branch = self.client().branch_info()
        except GiteeError as error:
            self.send_json(HTTPStatus.BAD_GATEWAY, {"ok": False, "error": error.message, "status": error.status})
            return
        self.send_json(
            HTTPStatus.OK,
            {
                "ok": True,
                "repo": {"owner": self.config.owner, "name": self.config.repo, "private": repo.get("private"), "default_branch": repo.get("default_branch")},
                "branch": {"name": self.config.branch, "exists": bool(branch)},
                "dataFiles": DATA_FILES,
            },
        )

    def handle_sync_data(self) -> None:
        if not self.require_app_key():
            return
        client = self.client()
        data: dict[str, Any] = {}
        files: dict[str, Any] = {}
        try:
            for domain, remote_path in DATA_FILES.items():
                payload, info = client.get_json(remote_path)
                data[domain] = payload
                files[domain] = {"path": remote_path, "sha": info.get("sha") if info else None, "missing": info is None}
        except (GiteeError, json.JSONDecodeError) as error:
            self.send_json(HTTPStatus.BAD_GATEWAY, {"ok": False, "error": str(error)})
            return
        self.send_json(HTTPStatus.OK, {"ok": True, "data": data, "files": files})

    def handle_sync_domain_get(self, domain: str) -> None:
        if not self.require_app_key():
            return
        if domain not in DATA_FILES:
            self.send_json(HTTPStatus.NOT_FOUND, {"ok": False, "error": "unknown data domain"})
            return
        try:
            payload, info = self.client().get_json(DATA_FILES[domain])
        except (GiteeError, json.JSONDecodeError) as error:
            self.send_json(HTTPStatus.BAD_GATEWAY, {"ok": False, "error": str(error)})
            return
        self.send_json(HTTPStatus.OK, {"ok": True, "domain": domain, "path": DATA_FILES[domain], "data": payload, "sha": info.get("sha") if info else None})

    def handle_sync_domain_save(self, domain: str) -> None:
        if not self.require_app_key():
            return
        if domain not in DATA_FILES:
            self.send_json(HTTPStatus.NOT_FOUND, {"ok": False, "error": "unknown data domain"})
            return
        try:
            body = self.read_json_body()
            payload = body.get("data", body) if isinstance(body, dict) else body
            result = self.client().put_json(DATA_FILES[domain], payload, f"sync update: {DATA_FILES[domain]}")
        except OverflowError as error:
            self.send_json(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, {"ok": False, "error": str(error)})
            return
        except (ValueError, json.JSONDecodeError) as error:
            self.send_json(HTTPStatus.BAD_REQUEST, {"ok": False, "error": str(error)})
            return
        except GiteeError as error:
            self.send_json(HTTPStatus.BAD_GATEWAY, {"ok": False, "error": error.message, "status": error.status})
            return
        self.send_json(HTTPStatus.OK, {"ok": True, "domain": domain, "path": DATA_FILES[domain], "sha": (result.get("content") or {}).get("sha")})


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Serve dashboard and sync JSON data through Gitee.")
    parser.add_argument("--host")
    parser.add_argument("--port", type=int)
    parser.add_argument("--init-gitee", action="store_true", help="Initialize/update configured Gitee data files from local JSON/SQLite.")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    config = SyncConfig.load()
    if args.host:
        config = SyncConfig(**{**config.__dict__, "host": args.host})
    if args.port:
        config = SyncConfig(**{**config.__dict__, "port": args.port})
    if args.init_gitee:
        result = init_gitee_data(GiteeClient(config))
        print(json.dumps({"ok": True, "initialized": result}, ensure_ascii=False, indent=2))
        return 0
    SyncHandler.config = config
    server = ThreadingHTTPServer((config.host, config.port), SyncHandler)
    print(f"Serving dashboard + sync API on http://{config.host}:{config.port}/apps/dashboard/index.html")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping sync service")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
