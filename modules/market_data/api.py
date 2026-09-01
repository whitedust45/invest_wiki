"""Public application interface for market-data consumers.

The ledger and short-term modules use this facade instead of importing data
providers, SQLite tables, or wiki parsing internals directly.
"""

from __future__ import annotations

import concurrent.futures
import json
import subprocess
import sys
import threading
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from modules.market_data.store import connect_market_data_db, upsert_instrument
from modules.market_data.watchlist import load_wiki_tracked_instruments


ROOT = Path(__file__).resolve().parents[2]
DASHBOARD_DATA_DIR = ROOT / "apps" / "dashboard" / "data"
POSITION_QUOTES_JSON = DASHBOARD_DATA_DIR / "position-quotes.json"
POSITION_HISTORY_JSON = DASHBOARD_DATA_DIR / "position-history.json"
QUOTES_CLI = ROOT / "modules" / "market_data" / "quotes.py"


def symbols_from_query(query: dict[str, list[str]], default: list[str] | None = None) -> list[str]:
    raw_values = [*query.get("symbols", []), *query.get("symbol", [])]
    symbols: list[str] = []
    for raw in raw_values:
        for part in str(raw).split(","):
            symbol = part.strip()
            if symbol and symbol not in symbols:
                symbols.append(symbol)
    return symbols or list(default or [])


def position_quotes_command(symbols: list[str]) -> list[str]:
    return [sys.executable, str(QUOTES_CLI), *symbols, "--output", str(POSITION_QUOTES_JSON)]


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


def read_projection(path: Path, default: dict[str, Any]) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return default


def seed_wiki_instruments(instruments: list[dict[str, Any]]) -> None:
    if not instruments:
        return
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
    instruments = load_wiki_tracked_instruments(ROOT)
    symbols = [item["symbol"] for item in instruments]
    if not symbols:
        return {"symbols": [], "quotes": 0, "history_rows": 0, "errors": {"watchlist": "no wiki instruments found"}}
    seed_wiki_instruments(instruments)
    completed = subprocess.run(
        position_history_command(symbols, days=days, end_date=end_date),
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
        timeout=600,
    )
    quotes_payload = read_projection(POSITION_QUOTES_JSON, {"quotes": {}, "errors": {}})
    history_payload = read_projection(POSITION_HISTORY_JSON, {"histories": {}, "errors": {}})
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
    """Bounded in-memory job registry for long-running public data refreshes."""

    def __init__(self, max_workers: int = 2, max_jobs: int = 200):
        self.executor = concurrent.futures.ThreadPoolExecutor(max_workers=max_workers, thread_name_prefix="market-data")
        self.lock = threading.Lock()
        self.jobs: dict[str, dict[str, Any]] = {}
        self.max_jobs = max(1, int(max_jobs))

    def submit(self, job_type: str, func: Any, **params: Any) -> dict[str, Any]:
        job_id = f"{job_type}-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S')}-{uuid.uuid4().hex[:12]}"
        job = {"id": job_id, "type": job_type, "status": "queued", "created_at": datetime.now(timezone.utc).isoformat(), "started_at": None, "finished_at": None, "params": params, "result": None, "error": None}
        with self.lock:
            self.jobs[job_id] = job
            self._prune_finished_jobs_locked()

        def run_job() -> Any:
            with self.lock:
                self.jobs[job_id] = {**self.jobs[job_id], "status": "running", "started_at": datetime.now(timezone.utc).isoformat()}
            try:
                result = func(**params)
            except Exception as error:  # noqa: BLE001 - caller reads the job state
                with self.lock:
                    self.jobs[job_id] = {**self.jobs[job_id], "status": "failed", "finished_at": datetime.now(timezone.utc).isoformat(), "error": str(error)}
                    self._prune_finished_jobs_locked()
                raise
            with self.lock:
                self.jobs[job_id] = {**self.jobs[job_id], "status": "success", "finished_at": datetime.now(timezone.utc).isoformat(), "result": result}
                self._prune_finished_jobs_locked()
            return result

        self.executor.submit(run_job)
        return job

    def get(self, job_id: str) -> dict[str, Any] | None:
        with self.lock:
            job = self.jobs.get(job_id)
            return dict(job) if job else None

    def wait(self, job_id: str, timeout: float) -> dict[str, Any] | None:
        deadline = datetime.now(timezone.utc).timestamp() + timeout
        while datetime.now(timezone.utc).timestamp() < deadline:
            job = self.get(job_id)
            if job and job["status"] in {"success", "failed"}:
                return job
        return self.get(job_id)

    def shutdown(self) -> None:
        self.executor.shutdown(wait=True)

    def _prune_finished_jobs_locked(self) -> None:
        finished = [item for item in self.jobs.values() if item["status"] in {"success", "failed"}]
        finished.sort(key=lambda item: str(item.get("finished_at") or ""), reverse=True)
        for item in finished[self.max_jobs:]:
            self.jobs.pop(item["id"], None)
