"""SQLite store for long-lived dashboard market data.

The store keeps market facts traceable while preserving the existing JSON
contracts as projection outputs. Tables intentionally use redundant business
keys instead of foreign keys.
"""

from __future__ import annotations

import json
import re
import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
MARKET_DATA_DB = ROOT / "apps" / "dashboard" / "data" / "market-data.db"

TUSHARE_PREFIXES = ("Tushare",)
LEGACY_SOURCES = ("legacy_json", "本地历史JSON", "Sina关注列表快照")
TENCENT_PREFIXES = ("腾讯",)
SINA_PREFIXES = ("新浪",)
YAHOO_PREFIXES = ("Yahoo",)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def json_dumps(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def connect_market_data_db(path: Path | str = MARKET_DATA_DB) -> sqlite3.Connection:
    db_path = Path(path)
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path)
    initialize_schema(conn)
    return conn


def initialize_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS tracked_instruments (
          instrument_key TEXT PRIMARY KEY,
          symbol TEXT NOT NULL,
          ts_code TEXT,
          name TEXT,
          market TEXT NOT NULL,
          asset_type TEXT NOT NULL,
          currency TEXT,
          exchange TEXT,
          track_scope TEXT NOT NULL,
          active INTEGER NOT NULL DEFAULT 1,
          source TEXT,
          first_seen_at TEXT NOT NULL,
          last_seen_at TEXT NOT NULL,
          raw_json TEXT
        );

        CREATE UNIQUE INDEX IF NOT EXISTS idx_tracked_instruments_symbol_market
        ON tracked_instruments(symbol, market);

        CREATE INDEX IF NOT EXISTS idx_tracked_instruments_scope_active
        ON tracked_instruments(track_scope, active);

        CREATE TABLE IF NOT EXISTS sync_runs (
          run_id TEXT PRIMARY KEY,
          run_type TEXT NOT NULL,
          status TEXT NOT NULL,
          started_at TEXT NOT NULL,
          finished_at TEXT,
          requested_symbols TEXT,
          source_priority_json TEXT,
          params_json TEXT,
          summary_json TEXT,
          error_summary TEXT
        );

        CREATE TABLE IF NOT EXISTS quote_snapshots (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          instrument_key TEXT NOT NULL,
          symbol TEXT NOT NULL,
          name TEXT,
          market TEXT,
          asset_type TEXT,
          quote_date TEXT NOT NULL,
          quote_time TEXT,
          price REAL NOT NULL,
          raw_price REAL,
          currency TEXT,
          fx_rate REAL,
          prev_close REAL,
          change_pct REAL,
          volume REAL,
          amount REAL,
          source TEXT NOT NULL,
          run_id TEXT NOT NULL,
          first_seen_at TEXT NOT NULL,
          last_seen_at TEXT NOT NULL,
          revision_count INTEGER NOT NULL DEFAULT 0,
          raw_json TEXT
        );

        CREATE UNIQUE INDEX IF NOT EXISTS idx_quote_snapshots_unique
        ON quote_snapshots(symbol, quote_time, source);

        CREATE TABLE IF NOT EXISTS daily_bars (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          instrument_key TEXT NOT NULL,
          symbol TEXT NOT NULL,
          name TEXT,
          market TEXT,
          asset_type TEXT,
          trade_date TEXT NOT NULL,
          open REAL,
          high REAL,
          low REAL,
          close REAL NOT NULL,
          volume REAL,
          amount REAL,
          change_pct REAL,
          adjustment TEXT NOT NULL DEFAULT 'none',
          source TEXT NOT NULL,
          run_id TEXT NOT NULL,
          first_seen_at TEXT NOT NULL,
          last_seen_at TEXT NOT NULL,
          revision_count INTEGER NOT NULL DEFAULT 0,
          raw_json TEXT
        );

        CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_bars_unique
        ON daily_bars(symbol, trade_date, adjustment, source);

        CREATE INDEX IF NOT EXISTS idx_daily_bars_symbol_date
        ON daily_bars(symbol, trade_date);

        CREATE TABLE IF NOT EXISTS daily_metrics (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          instrument_key TEXT NOT NULL,
          symbol TEXT NOT NULL,
          name TEXT,
          market TEXT,
          asset_type TEXT,
          trade_date TEXT NOT NULL,
          metric_name TEXT NOT NULL,
          metric_value REAL,
          metric_unit TEXT,
          source TEXT NOT NULL,
          run_id TEXT NOT NULL,
          first_seen_at TEXT NOT NULL,
          last_seen_at TEXT NOT NULL,
          revision_count INTEGER NOT NULL DEFAULT 0,
          raw_json TEXT
        );

        CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_metrics_unique
        ON daily_metrics(symbol, trade_date, metric_name, source);

        CREATE INDEX IF NOT EXISTS idx_daily_metrics_lookup
        ON daily_metrics(symbol, metric_name, trade_date);

        CREATE TABLE IF NOT EXISTS adjustment_factors (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          instrument_key TEXT NOT NULL,
          symbol TEXT NOT NULL,
          name TEXT,
          market TEXT,
          asset_type TEXT,
          trade_date TEXT NOT NULL,
          adj_factor REAL NOT NULL,
          source TEXT NOT NULL,
          run_id TEXT NOT NULL,
          first_seen_at TEXT NOT NULL,
          last_seen_at TEXT NOT NULL,
          revision_count INTEGER NOT NULL DEFAULT 0,
          raw_json TEXT
        );

        CREATE UNIQUE INDEX IF NOT EXISTS idx_adjustment_factors_unique
        ON adjustment_factors(symbol, trade_date, source);

        CREATE TABLE IF NOT EXISTS derived_indicators (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          instrument_key TEXT NOT NULL,
          symbol TEXT NOT NULL,
          name TEXT,
          market TEXT,
          asset_type TEXT,
          as_of_date TEXT NOT NULL,
          indicator_name TEXT NOT NULL,
          indicator_value REAL,
          indicator_text TEXT,
          window TEXT,
          source_scope TEXT NOT NULL,
          run_id TEXT NOT NULL,
          first_seen_at TEXT NOT NULL,
          last_seen_at TEXT NOT NULL,
          revision_count INTEGER NOT NULL DEFAULT 0,
          raw_json TEXT
        );

        CREATE UNIQUE INDEX IF NOT EXISTS idx_derived_indicators_unique
        ON derived_indicators(symbol, as_of_date, indicator_name, source_scope);

        CREATE TABLE IF NOT EXISTS basis_snapshots (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          underlying_key TEXT NOT NULL,
          underlying_symbol TEXT NOT NULL,
          underlying_name TEXT,
          future_key TEXT NOT NULL,
          future_symbol TEXT NOT NULL,
          trade_date TEXT NOT NULL,
          spot_price REAL,
          future_price REAL,
          basis REAL,
          annualized_basis_pct REAL,
          maturity_date TEXT,
          days_left INTEGER,
          roll_window INTEGER NOT NULL DEFAULT 0,
          roll_alert INTEGER NOT NULL DEFAULT 0,
          source TEXT NOT NULL,
          run_id TEXT NOT NULL,
          first_seen_at TEXT NOT NULL,
          last_seen_at TEXT NOT NULL,
          revision_count INTEGER NOT NULL DEFAULT 0,
          raw_json TEXT
        );

        CREATE UNIQUE INDEX IF NOT EXISTS idx_basis_snapshots_unique
        ON basis_snapshots(underlying_symbol, future_symbol, trade_date, source);

        CREATE INDEX IF NOT EXISTS idx_basis_snapshots_underlying_date
        ON basis_snapshots(underlying_symbol, trade_date);

        CREATE TABLE IF NOT EXISTS source_events (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          run_id TEXT NOT NULL,
          event_time TEXT NOT NULL,
          event_type TEXT NOT NULL,
          severity TEXT NOT NULL,
          instrument_key TEXT,
          symbol TEXT,
          source TEXT,
          api_name TEXT,
          message TEXT NOT NULL,
          detail_json TEXT
        );

        CREATE UNIQUE INDEX IF NOT EXISTS idx_source_events_unique
        ON source_events(run_id, symbol, source, api_name, event_type);

        CREATE TABLE IF NOT EXISTS projection_exports (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          projection_name TEXT NOT NULL,
          generated_at TEXT NOT NULL,
          output_path TEXT NOT NULL,
          run_id TEXT NOT NULL,
          row_count INTEGER NOT NULL DEFAULT 0,
          source_priority_json TEXT,
          params_json TEXT,
          summary_json TEXT
        );

        CREATE INDEX IF NOT EXISTS idx_projection_exports_name_time
        ON projection_exports(projection_name, generated_at);
        """
    )
    conn.commit()


def market_for_symbol(symbol: str, ts_code: str = "") -> str:
    code = symbol.strip().upper()
    ts = ts_code.strip().upper()
    if ts.endswith(".SZ") or re.match(r"^(000|001|002|003|159|300|301)\d{3}$", code):
        return "SZ"
    if ts.endswith(".SH") or re.match(r"^(510|511|512|513|515|516|517|518|519|520|560|561|562|563|588|600|601|603|605|688|689)\d{3}$", code):
        return "SH"
    if code.startswith(("IC", "IM", "IF", "IH")):
        return "CFFEX"
    if code in {"CNY=X", "USDCNY"}:
        return "FX"
    return "US" if re.fullmatch(r"[A-Z][A-Z0-9.-]{0,9}", code) else "UNKNOWN"


def asset_type_for_symbol(symbol: str, market: str) -> str:
    code = symbol.strip().upper()
    if market == "FX":
        return "fx"
    if market == "CFFEX":
        return "future"
    if market == "US":
        return "us_etf"
    if code in {"000905", "000852"}:
        return "index"
    if re.match(r"^(159|5)", code):
        return "etf"
    return "stock"


def instrument_key_for(symbol: str, market: str) -> str:
    code = symbol.strip().upper()
    if market in {"SZ", "SH"}:
        return f"CN:{market}:{code}"
    if market == "CFFEX":
        return f"CFFEX:{code}"
    if market == "FX":
        return f"FX:{code}"
    if market == "US":
        return f"US:UNKNOWN:{code}"
    return f"UNKNOWN:{code}"


def instrument_from_payload(symbol: str, payload: dict[str, Any] | None = None) -> dict[str, str]:
    payload = payload or {}
    code = str(payload.get("symbol") or symbol).strip()
    ts_code = str(payload.get("ts_code") or "")
    market = market_for_symbol(code, ts_code)
    asset_type = str(payload.get("asset_type") or asset_type_for_symbol(code, market))
    return {
        "instrument_key": str(payload.get("instrument_key") or instrument_key_for(code, market)),
        "symbol": code.upper() if market in {"US", "FX", "CFFEX"} else code,
        "ts_code": ts_code,
        "name": str(payload.get("name") or code),
        "market": market,
        "asset_type": asset_type,
        "currency": str(payload.get("currency") or ("CNY" if market in {"SZ", "SH", "CFFEX"} else "USD" if market == "US" else "")),
    }


def upsert_instrument(
    conn: sqlite3.Connection,
    symbol: str,
    payload: dict[str, Any] | None = None,
    source: str = "",
    track_scope: str = "watchlist",
) -> dict[str, str]:
    now = utc_now()
    instrument = instrument_from_payload(symbol, payload)
    conn.execute(
        """
        INSERT INTO tracked_instruments (
          instrument_key, symbol, ts_code, name, market, asset_type, currency,
          exchange, track_scope, active, source, first_seen_at, last_seen_at, raw_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)
        ON CONFLICT(instrument_key) DO UPDATE SET
          ts_code = COALESCE(NULLIF(excluded.ts_code, ''), tracked_instruments.ts_code),
          name = COALESCE(NULLIF(excluded.name, ''), tracked_instruments.name),
          market = excluded.market,
          asset_type = excluded.asset_type,
          currency = COALESCE(NULLIF(excluded.currency, ''), tracked_instruments.currency),
          track_scope = CASE
            WHEN excluded.track_scope = 'watchlist'
              AND tracked_instruments.track_scope LIKE 'wiki_%'
            THEN tracked_instruments.track_scope
            ELSE excluded.track_scope END,
          active = 1,
          source = CASE
            WHEN excluded.track_scope = 'watchlist'
              AND tracked_instruments.track_scope LIKE 'wiki_%'
              AND COALESCE(tracked_instruments.source, '') != ''
            THEN tracked_instruments.source
            ELSE COALESCE(NULLIF(excluded.source, ''), tracked_instruments.source) END,
          last_seen_at = excluded.last_seen_at,
          raw_json = COALESCE(excluded.raw_json, tracked_instruments.raw_json)
        """,
        (
            instrument["instrument_key"],
            instrument["symbol"],
            instrument["ts_code"],
            instrument["name"],
            instrument["market"],
            instrument["asset_type"],
            instrument["currency"],
            instrument["market"],
            track_scope,
            source,
            now,
            now,
            json_dumps(payload or {}),
        ),
    )
    return instrument


def start_sync_run(
    conn: sqlite3.Connection,
    run_type: str,
    requested_symbols: list[str] | None = None,
    params: dict[str, Any] | None = None,
) -> str:
    run_id = f"{run_type}-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S')}-{uuid.uuid4().hex[:8]}"
    conn.execute(
        """
        INSERT INTO sync_runs (
          run_id, run_type, status, started_at, requested_symbols,
          source_priority_json, params_json
        ) VALUES (?, ?, 'running', ?, ?, ?, ?)
        """,
        (
            run_id,
            run_type,
            utc_now(),
            json_dumps(requested_symbols or []),
            json_dumps(source_priority()),
            json_dumps(params or {}),
        ),
    )
    conn.commit()
    return run_id


def finish_sync_run(
    conn: sqlite3.Connection,
    run_id: str,
    status: str,
    summary: dict[str, Any] | None = None,
    error_summary: str = "",
) -> None:
    conn.execute(
        """
        UPDATE sync_runs
        SET status = ?, finished_at = ?, summary_json = ?, error_summary = ?
        WHERE run_id = ?
        """,
        (status, utc_now(), json_dumps(summary or {}), error_summary, run_id),
    )
    conn.commit()


def maybe_float(value: Any) -> float | None:
    if value is None or value == "":
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def quote_date_from_time(value: str) -> str:
    text = str(value or "").strip()
    if re.fullmatch(r"\d{8}", text):
        return f"{text[:4]}-{text[4:6]}-{text[6:8]}"
    match = re.search(r"\d{4}-\d{2}-\d{2}", text)
    return match.group(0) if match else datetime.now(timezone.utc).date().isoformat()


def upsert_quote_snapshot(conn: sqlite3.Connection, quote: dict[str, Any], run_id: str) -> None:
    symbol = str(quote.get("symbol") or "").strip()
    if not symbol:
        return
    source = str(quote.get("source") or "unknown")
    quote_time = str(quote.get("trade_time") or quote.get("quote_time") or quote.get("date") or quote.get("trade_date") or quote_date_from_time(""))
    quote_date = quote_date_from_time(quote_time)
    price = maybe_float(quote.get("price"))
    if price is None:
        return
    instrument = upsert_instrument(conn, symbol, quote, source=source)
    now = utc_now()
    values = (
        instrument["instrument_key"],
        instrument["symbol"],
        instrument["name"],
        instrument["market"],
        instrument["asset_type"],
        quote_date,
        quote_time,
        price,
        maybe_float(quote.get("raw_price")),
        str(quote.get("raw_currency") or quote.get("currency") or instrument["currency"]),
        maybe_float(quote.get("usd_cny") or quote.get("fx_rate")),
        maybe_float(quote.get("prev_close")),
        maybe_float(quote.get("change_pct")),
        maybe_float(quote.get("volume")),
        maybe_float(quote.get("amount")),
        source,
        run_id,
        now,
        now,
        json_dumps(quote),
    )
    conn.execute(
        """
        INSERT INTO quote_snapshots (
          instrument_key, symbol, name, market, asset_type, quote_date, quote_time,
          price, raw_price, currency, fx_rate, prev_close, change_pct, volume,
          amount, source, run_id, first_seen_at, last_seen_at, raw_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(symbol, quote_time, source) DO UPDATE SET
          name = excluded.name,
          market = excluded.market,
          asset_type = excluded.asset_type,
          price = excluded.price,
          raw_price = excluded.raw_price,
          currency = excluded.currency,
          fx_rate = excluded.fx_rate,
          prev_close = excluded.prev_close,
          change_pct = excluded.change_pct,
          volume = excluded.volume,
          amount = excluded.amount,
          run_id = excluded.run_id,
          last_seen_at = excluded.last_seen_at,
          revision_count = quote_snapshots.revision_count + CASE
            WHEN quote_snapshots.price IS NOT excluded.price
              OR quote_snapshots.raw_price IS NOT excluded.raw_price
              OR quote_snapshots.prev_close IS NOT excluded.prev_close
              OR quote_snapshots.change_pct IS NOT excluded.change_pct
            THEN 1 ELSE 0 END,
          raw_json = excluded.raw_json
        """,
        values,
    )
    conn.commit()


def row_date(row: dict[str, Any]) -> str:
    text = str(row.get("date") or row.get("trade_date") or "")
    if re.fullmatch(r"\d{8}", text):
        return f"{text[:4]}-{text[4:6]}-{text[6:8]}"
    return text


def upsert_daily_bar(
    conn: sqlite3.Connection,
    symbol: str,
    row: dict[str, Any],
    run_id: str,
    adjustment: str = "none",
) -> None:
    code = str(row.get("symbol") or symbol).strip()
    trade_date = row_date(row)
    close = maybe_float(row.get("close"))
    if not code or not trade_date or close is None:
        return
    source = str(row.get("source") or "unknown")
    instrument = upsert_instrument(conn, code, row, source=source)
    now = utc_now()
    values = (
        instrument["instrument_key"],
        instrument["symbol"],
        instrument["name"],
        instrument["market"],
        instrument["asset_type"],
        trade_date,
        maybe_float(row.get("open")),
        maybe_float(row.get("high")),
        maybe_float(row.get("low")),
        close,
        maybe_float(row.get("volume")),
        maybe_float(row.get("amount")),
        maybe_float(row.get("change_pct")),
        adjustment,
        source,
        run_id,
        now,
        now,
        json_dumps(row),
    )
    conn.execute(
        """
        INSERT INTO daily_bars (
          instrument_key, symbol, name, market, asset_type, trade_date, open,
          high, low, close, volume, amount, change_pct, adjustment, source,
          run_id, first_seen_at, last_seen_at, raw_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(symbol, trade_date, adjustment, source) DO UPDATE SET
          name = excluded.name,
          market = excluded.market,
          asset_type = excluded.asset_type,
          open = excluded.open,
          high = excluded.high,
          low = excluded.low,
          close = excluded.close,
          volume = excluded.volume,
          amount = excluded.amount,
          change_pct = excluded.change_pct,
          run_id = excluded.run_id,
          last_seen_at = excluded.last_seen_at,
          revision_count = daily_bars.revision_count + CASE
            WHEN daily_bars.open IS NOT excluded.open
              OR daily_bars.high IS NOT excluded.high
              OR daily_bars.low IS NOT excluded.low
              OR daily_bars.close IS NOT excluded.close
              OR daily_bars.volume IS NOT excluded.volume
              OR daily_bars.amount IS NOT excluded.amount
              OR daily_bars.change_pct IS NOT excluded.change_pct
            THEN 1 ELSE 0 END,
          raw_json = excluded.raw_json
        """,
        values,
    )
    conn.commit()


def record_source_event(
    conn: sqlite3.Connection,
    run_id: str,
    event_type: str,
    severity: str,
    message: str,
    symbol: str = "",
    source: str = "",
    api_name: str = "",
    detail: dict[str, Any] | None = None,
) -> None:
    instrument_key = instrument_key_for(symbol, market_for_symbol(symbol)) if symbol else None
    conn.execute(
        """
        INSERT INTO source_events (
          run_id, event_time, event_type, severity, instrument_key, symbol,
          source, api_name, message, detail_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(run_id, symbol, source, api_name, event_type) DO UPDATE SET
          event_time = excluded.event_time,
          severity = excluded.severity,
          message = excluded.message,
          detail_json = excluded.detail_json
        """,
        (
            run_id,
            utc_now(),
            event_type,
            severity,
            instrument_key,
            symbol,
            source,
            api_name,
            message,
            json_dumps(detail or {}),
        ),
    )
    conn.commit()


def upsert_daily_metric(
    conn: sqlite3.Connection,
    symbol: str,
    metric_name: str,
    metric_value: Any,
    trade_date: str,
    source: str,
    run_id: str,
    payload: dict[str, Any] | None = None,
) -> None:
    value = maybe_float(metric_value)
    if value is None:
        return
    instrument = upsert_instrument(conn, symbol, payload or {}, source=source, track_scope="system")
    now = utc_now()
    conn.execute(
        """
        INSERT INTO daily_metrics (
          instrument_key, symbol, name, market, asset_type, trade_date,
          metric_name, metric_value, metric_unit, source, run_id,
          first_seen_at, last_seen_at, raw_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(symbol, trade_date, metric_name, source) DO UPDATE SET
          metric_value = excluded.metric_value,
          run_id = excluded.run_id,
          last_seen_at = excluded.last_seen_at,
          revision_count = daily_metrics.revision_count + CASE
            WHEN daily_metrics.metric_value IS NOT excluded.metric_value THEN 1 ELSE 0 END,
          raw_json = excluded.raw_json
        """,
        (
            instrument["instrument_key"],
            instrument["symbol"],
            instrument["name"],
            instrument["market"],
            instrument["asset_type"],
            quote_date_from_time(trade_date),
            metric_name,
            value,
            "",
            source or "unknown",
            run_id,
            now,
            now,
            json_dumps(payload or {}),
        ),
    )
    conn.commit()


def upsert_derived_indicator(
    conn: sqlite3.Connection,
    symbol: str,
    indicator_name: str,
    indicator_value: Any,
    as_of_date: str,
    source_scope: str,
    run_id: str,
    payload: dict[str, Any] | None = None,
    window: str = "",
) -> None:
    value = maybe_float(indicator_value)
    if value is None:
        return
    instrument = upsert_instrument(conn, symbol, payload or {}, source=source_scope, track_scope="system")
    now = utc_now()
    conn.execute(
        """
        INSERT INTO derived_indicators (
          instrument_key, symbol, name, market, asset_type, as_of_date,
          indicator_name, indicator_value, indicator_text, window,
          source_scope, run_id, first_seen_at, last_seen_at, raw_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(symbol, as_of_date, indicator_name, source_scope) DO UPDATE SET
          indicator_value = excluded.indicator_value,
          indicator_text = excluded.indicator_text,
          window = excluded.window,
          run_id = excluded.run_id,
          last_seen_at = excluded.last_seen_at,
          revision_count = derived_indicators.revision_count + CASE
            WHEN derived_indicators.indicator_value IS NOT excluded.indicator_value
              OR derived_indicators.indicator_text IS NOT excluded.indicator_text
            THEN 1 ELSE 0 END,
          raw_json = excluded.raw_json
        """,
        (
            instrument["instrument_key"],
            instrument["symbol"],
            instrument["name"],
            instrument["market"],
            instrument["asset_type"],
            quote_date_from_time(as_of_date),
            indicator_name,
            value,
            "",
            window,
            source_scope or "unknown",
            run_id,
            now,
            now,
            json_dumps(payload or {}),
        ),
    )
    conn.commit()


def upsert_basis_snapshot(
    conn: sqlite3.Connection,
    underlying_symbol: str,
    underlying_name: str,
    future_symbol: str,
    trade_date: str,
    contract: dict[str, Any],
    source: str,
    run_id: str,
) -> None:
    if not future_symbol:
        return
    underlying = upsert_instrument(
        conn,
        underlying_symbol,
        {"symbol": underlying_symbol, "name": underlying_name, "asset_type": "index"},
        source=source,
        track_scope="system",
    )
    future = upsert_instrument(
        conn,
        future_symbol,
        {"symbol": future_symbol, "name": future_symbol, "asset_type": "future"},
        source=source,
        track_scope="system",
    )
    notice = contract.get("roll_notice") or {}
    now = utc_now()
    conn.execute(
        """
        INSERT INTO basis_snapshots (
          underlying_key, underlying_symbol, underlying_name, future_key,
          future_symbol, trade_date, spot_price, future_price, basis,
          annualized_basis_pct, maturity_date, days_left, roll_window,
          roll_alert, source, run_id, first_seen_at, last_seen_at, raw_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(underlying_symbol, future_symbol, trade_date, source) DO UPDATE SET
          spot_price = excluded.spot_price,
          future_price = excluded.future_price,
          basis = excluded.basis,
          annualized_basis_pct = excluded.annualized_basis_pct,
          maturity_date = excluded.maturity_date,
          days_left = excluded.days_left,
          roll_window = excluded.roll_window,
          roll_alert = excluded.roll_alert,
          run_id = excluded.run_id,
          last_seen_at = excluded.last_seen_at,
          revision_count = basis_snapshots.revision_count + CASE
            WHEN basis_snapshots.spot_price IS NOT excluded.spot_price
              OR basis_snapshots.future_price IS NOT excluded.future_price
              OR basis_snapshots.basis IS NOT excluded.basis
              OR basis_snapshots.annualized_basis_pct IS NOT excluded.annualized_basis_pct
            THEN 1 ELSE 0 END,
          raw_json = excluded.raw_json
        """,
        (
            underlying["instrument_key"],
            underlying["symbol"],
            underlying_name,
            future["instrument_key"],
            future_symbol,
            quote_date_from_time(trade_date),
            maybe_float(contract.get("spot")),
            maybe_float(contract.get("future")),
            maybe_float(contract.get("basis")),
            maybe_float(contract.get("annualized_basis_pct")),
            contract.get("delivery_date") or contract.get("maturity_date") or "",
            int(contract.get("days_left") or 0),
            1 if notice.get("level") in {"watch", "alert"} else 0,
            1 if notice.get("level") == "alert" else 0,
            source or "unknown",
            run_id,
            now,
            now,
            json_dumps(contract),
        ),
    )
    conn.commit()


def import_valuation_payload(conn: sqlite3.Connection, payload: dict[str, Any], run_id: str) -> None:
    indexes = payload.get("indexes") or {}
    for key, item in indexes.items():
        symbol = str(item.get("code") or "")
        if not symbol:
            continue
        trade_date = str(item.get("trade_date") or payload.get("trade_date") or "")
        base_payload = {
            "symbol": symbol,
            "name": item.get("name") or key,
            "asset_type": "index",
        }
        upsert_daily_metric(conn, symbol, "pe", item.get("pe"), trade_date, str(item.get("pe_source") or "unknown"), run_id, base_payload)
        upsert_daily_metric(conn, symbol, "pb", item.get("pb"), trade_date, str(item.get("pb_source") or "unknown"), run_id, base_payload)
        upsert_derived_indicator(
            conn,
            symbol,
            "pe_percentile",
            item.get("pe_percentile"),
            trade_date,
            str(item.get("pe_source") or "unknown"),
            run_id,
            base_payload,
            window=str(item.get("history_window_years") or ""),
        )
        upsert_derived_indicator(
            conn,
            symbol,
            "pb_percentile",
            item.get("pb_percentile"),
            trade_date,
            str(item.get("pb_percentile_source") or "unknown"),
            run_id,
            base_payload,
            window=str(item.get("history_window_years") or ""),
        )
        if item.get("eastmoney_fallback_error"):
            record_source_event(
                conn,
                run_id,
                "error",
                "warn",
                str(item.get("eastmoney_fallback_error")),
                symbol=symbol,
                source="eastmoney_current_fallback",
                api_name="valuation",
            )
        basis = item.get("basis") or {}
        basis_source = str(basis.get("source") or "basis")
        for contract in basis.get("contracts") or []:
            upsert_basis_snapshot(
                conn,
                symbol,
                str(item.get("name") or key),
                str(contract.get("contract") or ""),
                trade_date,
                contract,
                basis_source,
                run_id,
            )


def import_legacy_payloads(
    conn: sqlite3.Connection,
    quotes_payload: dict[str, Any] | None,
    history_payload: dict[str, Any] | None,
    valuation_payload: dict[str, Any] | None,
    run_id: str,
) -> None:
    if quotes_payload:
        for symbol, quote in (quotes_payload.get("quotes") or {}).items():
            next_quote = dict(quote)
            next_quote.setdefault("symbol", symbol)
            next_quote.setdefault("source", quote.get("source") or "legacy_json")
            upsert_quote_snapshot(conn, next_quote, run_id)
        for symbol, message in (quotes_payload.get("errors") or {}).items():
            record_source_event(conn, run_id, "error", "warn", str(message), symbol=symbol, source="legacy_json")
    if history_payload:
        for symbol, rows in (history_payload.get("histories") or {}).items():
            for row in rows or []:
                next_row = dict(row)
                next_row.setdefault("source", row.get("source") or "legacy_json")
                upsert_daily_bar(conn, symbol, next_row, run_id, adjustment="qfq")
        for symbol, message in (history_payload.get("errors") or {}).items():
            record_source_event(conn, run_id, "error", "warn", str(message), symbol=symbol, source="legacy_json")
    if valuation_payload:
        import_valuation_payload(conn, valuation_payload, run_id)


def source_priority() -> list[str]:
    return ["Tushare", "legacy_json", "Sina", "Tencent", "Yahoo"]


def source_rank(source: str) -> int:
    text = source or ""
    if text.startswith(TUSHARE_PREFIXES):
        return 0
    if text in LEGACY_SOURCES or any(part in text for part in LEGACY_SOURCES):
        return 1
    if text.startswith(SINA_PREFIXES):
        return 2
    if text.startswith(TENCENT_PREFIXES):
        return 3
    if text.startswith(YAHOO_PREFIXES):
        return 4
    return 50


def export_position_quotes(conn: sqlite3.Connection, symbols: list[str] | None = None) -> dict[str, Any]:
    params: list[Any] = []
    where = ""
    if symbols:
        where = f"WHERE symbol IN ({','.join('?' for _ in symbols)})"
        params.extend(symbols)
    rows = conn.execute(
        f"""
        SELECT symbol, name, price, raw_price, currency, fx_rate, prev_close,
               change_pct, volume, amount, source, quote_time, quote_date
        FROM quote_snapshots
        {where}
        ORDER BY symbol, quote_date DESC, quote_time DESC
        """,
        params,
    ).fetchall()
    best: dict[str, tuple[tuple[str, str], int, tuple[Any, ...]]] = {}
    for row in rows:
        rank = source_rank(str(row[10] or ""))
        recency = (str(row[12] or ""), str(row[11] or ""))
        current = best.get(row[0])
        if current is None or recency > current[0] or (recency == current[0] and rank < current[1]):
            best[row[0]] = (recency, rank, row)
    quotes = {}
    for symbol, (_, _, row) in sorted(best.items()):
        quote = {
            "symbol": symbol,
            "name": row[1] or symbol,
            "price": row[2],
            "source": row[10],
            "trade_time": row[11],
        }
        if row[3] is not None:
            quote["raw_price"] = row[3]
        if row[4]:
            quote["raw_currency"] = row[4]
        if row[5] is not None:
            quote["usd_cny"] = row[5]
        if row[6] is not None:
            quote["prev_close"] = row[6]
        if row[7] is not None:
            quote["change_pct"] = row[7]
        if row[8] is not None:
            quote["volume"] = row[8]
        if row[9] is not None:
            quote["amount"] = row[9]
        quotes[symbol] = quote
    return {
        "generated_at": utc_now(),
        "source": {
            "name": "market-data-sqlite projection",
            "priority": source_priority(),
        },
        "quotes": quotes,
        "errors": {},
    }


def export_position_history(conn: sqlite3.Connection, symbols: list[str] | None = None) -> dict[str, Any]:
    params: list[Any] = []
    where = ""
    if symbols:
        where = f"WHERE symbol IN ({','.join('?' for _ in symbols)})"
        params.extend(symbols)
    rows = conn.execute(
        f"""
        SELECT symbol, trade_date, open, close, high, low, volume, amount,
               change_pct, source, adjustment
        FROM daily_bars
        {where}
        ORDER BY symbol, trade_date, adjustment, source
        """,
        params,
    ).fetchall()
    selected: dict[tuple[str, str], tuple[tuple[int, int], tuple[Any, ...]]] = {}
    for row in rows:
        adjustment_rank = 0 if row[10] == "qfq" else 1
        rank = (adjustment_rank, source_rank(str(row[9] or "")))
        key = (row[0], row[1])
        current = selected.get(key)
        if current is None or rank < current[0]:
            selected[key] = (rank, row)
    histories: dict[str, list[dict[str, Any]]] = {}
    for (_, _), (_, row) in sorted(selected.items(), key=lambda item: (item[0][0], item[0][1])):
        item = {"date": row[1], "close": row[3], "source": row[9]}
        if row[2] is not None:
            item["open"] = row[2]
        if row[4] is not None:
            item["high"] = row[4]
        if row[5] is not None:
            item["low"] = row[5]
        if row[6] is not None:
            item["volume"] = row[6]
        if row[7] is not None:
            item["amount"] = row[7]
        if row[8] is not None:
            item["change_pct"] = row[8]
        histories.setdefault(row[0], []).append(item)
    return {
        "generated_at": utc_now(),
        "source": {
            "name": "market-data-sqlite projection daily close",
            "priority": ["qfq Tushare", "qfq legacy_json", "qfq Tencent", "none Tushare", "none legacy_json", "none Tencent"],
        },
        "histories": histories,
        "errors": {},
    }


def latest_metric(conn: sqlite3.Connection, symbol: str, metric_name: str) -> tuple[Any, str, str] | None:
    rows = conn.execute(
        """
        SELECT trade_date, metric_value, source
        FROM daily_metrics
        WHERE symbol = ? AND metric_name = ?
        ORDER BY trade_date DESC
        """,
        (symbol, metric_name),
    ).fetchall()
    best = None
    for row in rows:
        rank = source_rank(str(row[2] or ""))
        if best is None or (row[0], -rank) > (best[0][0], -best[1]):
            best = (row, rank)
    if best is None:
        return None
    row, _ = best
    return row[1], row[2], row[0]


def latest_indicator(conn: sqlite3.Connection, symbol: str, indicator_name: str) -> tuple[Any, str, str] | None:
    row = conn.execute(
        """
        SELECT as_of_date, indicator_value, source_scope
        FROM derived_indicators
        WHERE symbol = ? AND indicator_name = ?
        ORDER BY as_of_date DESC
        LIMIT 1
        """,
        (symbol, indicator_name),
    ).fetchone()
    if not row:
        return None
    return row[1], row[2], row[0]


def export_ic_im_valuation(conn: sqlite3.Connection) -> dict[str, Any]:
    now = utc_now()
    index_meta = {
        "IC": {"code": "000905", "name": "中证500", "underlying": "IC"},
        "IM": {"code": "000852", "name": "中证1000", "underlying": "IM"},
    }
    payload = {
        "schema_version": 1,
        "generated_at": now,
        "source": {
            "current_valuation": "market-data-sqlite daily_metrics projection",
            "pe_history": "market-data-sqlite derived_indicators projection",
            "pb_history": "market-data-sqlite derived_indicators projection",
            "basis": "market-data-sqlite basis_snapshots projection",
        },
        "strategy_rule": "PB percentile is the decision input; PE is auxiliary display only.",
        "trade_date": "",
        "indexes": {},
    }
    trade_dates: list[str] = []
    for key, meta in index_meta.items():
        symbol = meta["code"]
        pe = latest_metric(conn, symbol, "pe")
        pb = latest_metric(conn, symbol, "pb")
        pe_pct = latest_indicator(conn, symbol, "pe_percentile")
        pb_pct = latest_indicator(conn, symbol, "pb_percentile")
        candidates = [item[2] for item in (pe, pb, pe_pct, pb_pct) if item]
        trade_date = max(candidates) if candidates else ""
        if trade_date:
            trade_dates.append(trade_date)
        basis_rows = conn.execute(
            """
            SELECT future_symbol, spot_price, future_price, basis,
                   annualized_basis_pct, days_left, maturity_date, raw_json
            FROM basis_snapshots
            WHERE underlying_symbol = ?
              AND trade_date = (
                SELECT MAX(trade_date) FROM basis_snapshots WHERE underlying_symbol = ?
              )
            ORDER BY days_left, future_symbol
            """,
            (symbol, symbol),
        ).fetchall()
        contracts = []
        for row in basis_rows:
            raw = {}
            try:
                raw = json.loads(row[7] or "{}")
            except json.JSONDecodeError:
                raw = {}
            notice = raw.get("roll_notice") or {}
            contracts.append(
                {
                    "contract": row[0],
                    "spot": row[1],
                    "future": row[2],
                    "basis": row[3],
                    "annualized_basis_pct": row[4],
                    "days_left": row[5],
                    "delivery_date": row[6],
                    "roll_notice": notice,
                }
            )
        basis_payload = None
        if contracts:
            nearest = contracts[0]
            basis_payload = {
                "source": "market-data-sqlite basis_snapshots projection",
                "formula": "basis = spot - future; annualized = basis / future / days_left * 365",
                "roll_notice": {
                    "contract": nearest["contract"],
                    "delivery_date": nearest["delivery_date"],
                    "days_left": nearest["days_left"],
                    "level": (nearest.get("roll_notice") or {}).get("level", "normal"),
                    "message": (nearest.get("roll_notice") or {}).get("message", ""),
                },
                "contracts": contracts,
            }
        payload["indexes"][key] = {
            "code": symbol,
            "name": meta["name"],
            "underlying_future": meta["underlying"],
            "trade_date": trade_date,
            "pe": pe[0] if pe else None,
            "pb": pb[0] if pb else None,
            "pe_source": pe[1] if pe else None,
            "pb_source": pb[1] if pb else None,
            "eastmoney_fallback_error": None,
            "pe_percentile": pe_pct[0] if pe_pct else None,
            "pb_percentile": pb_pct[0] if pb_pct else None,
            "pb_percentile_source": pb_pct[1] if pb_pct else None,
            "pb_percentile_manual_required": pb_pct is None,
            "history_window_years": 10,
            "basis": basis_payload,
        }
    payload["trade_date"] = max(trade_dates) if trade_dates else ""
    return payload


def write_projection_record(
    conn: sqlite3.Connection,
    projection_name: str,
    output_path: Path,
    run_id: str,
    row_count: int,
    params: dict[str, Any] | None = None,
) -> None:
    conn.execute(
        """
        INSERT INTO projection_exports (
          projection_name, generated_at, output_path, run_id, row_count,
          source_priority_json, params_json, summary_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            projection_name,
            utc_now(),
            str(output_path),
            run_id,
            row_count,
            json_dumps(source_priority()),
            json_dumps(params or {}),
            json_dumps({"row_count": row_count}),
        ),
    )
    conn.commit()
