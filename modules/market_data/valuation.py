#!/usr/bin/env python3
"""Fetch IC/IM underlying index valuation data for the dashboard.

Official CSI endpoints provide current PE/PB and PE history. PB percentile is
only computed when a local PB history CSV is supplied, because the public CSI
frontend does not expose an obvious PB history endpoint.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
import sys
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.market_data.providers import TushareProClient, fetch_index_dailybasic
from modules.market_data.store import (
    connect_market_data_db,
    export_ic_im_valuation,
    finish_sync_run,
    import_valuation_payload,
    start_sync_run,
    write_projection_record,
)


CSI_HOME = "https://www.csindex.com.cn/csindex-home"
BASIS_HOME = "https://xags.stephenslab.top"
EASTMONEY_HOSTS = (
    "https://push2.eastmoney.com",
    "https://1.push2.eastmoney.com",
    "https://55.push2.eastmoney.com",
)
ROOT = Path(__file__).resolve().parents[2]
DASHBOARD_JSON = ROOT / "apps" / "dashboard" / "data" / "ic-im-valuation.json"
DEFAULT_HISTORY_DIR = ROOT / "apps" / "dashboard" / "data" / "history"
ROLL_WATCH_DAYS = 10
ROLL_ALERT_DAYS = 5

INDEXES = {
    "IC": {"code": "000905", "name": "中证500", "underlying": "IC"},
    "IM": {"code": "000852", "name": "中证1000", "underlying": "IM"},
}

BASIS_NODES = {
    "IC": {"node": "zzgz_qh", "spot": "sz399905"},
    "IM": {"node": "im_qh", "spot": "sh000852"},
}


def http_json(path: str, params: dict[str, str]) -> dict:
    url = f"{CSI_HOME}{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": "https://www.csindex.com.cn/",
            "Accept": "application/json,text/plain,*/*",
        },
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        body = resp.read().decode("utf-8")
    return json.loads(body)


def url_text(url: str, referer: str = "https://www.csindex.com.cn/") -> str:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": referer,
            "Accept": "application/json,text/plain,*/*",
        },
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.read().decode("utf-8", "ignore")


def percentile_rank(values: list[float], current: float) -> float | None:
    clean = [v for v in values if math.isfinite(v)]
    if not clean or not math.isfinite(current):
        return None
    count = sum(1 for v in clean if v <= current)
    return round(count / len(clean) * 100, 2)


def is_missing(value: object) -> bool:
    return value is None or value == "" or value == "-"


def number_or_none(value: object) -> float | None:
    if is_missing(value):
        return None
    try:
        num = float(value)
    except (TypeError, ValueError):
        return None
    return num if math.isfinite(num) else None


def quote_scale(value: object, scale: object) -> float | None:
    if is_missing(value):
        return None
    try:
        raw = float(value)
        divisor = 10 ** int(scale or 2)
    except (TypeError, ValueError):
        return None
    if not math.isfinite(raw) or divisor <= 0:
        return None
    return round(raw / divisor, 4)


def parse_json_or_jsonp(text: str) -> dict:
    text = text.strip()
    match = re.match(r"^[^(]+\((.*)\)\s*;?$", text, flags=re.S)
    if match:
        text = match.group(1)
    return json.loads(text)


def eastmoney_current_valuation(code: str) -> dict[str, object]:
    fields = "f57,f58,f162,f164,f167,f152"
    params = urllib.parse.urlencode({"secid": f"1.{code}", "fields": fields})
    last_error = None
    for host in EASTMONEY_HOSTS:
        url = f"{host}/api/qt/stock/get?{params}"
        try:
            data = parse_json_or_jsonp(url_text(url, referer=f"https://quote.eastmoney.com/zs{code}.html"))
        except Exception as exc:  # noqa: BLE001 - fallback source must never break the main script
            last_error = f"{type(exc).__name__}: {exc}"
            continue
        row = data.get("data") or {}
        scale = row.get("f152")
        return {
            "pb": quote_scale(row.get("f167"), scale),
            "pe_dynamic": quote_scale(row.get("f162"), scale),
            "pe_ttm": quote_scale(row.get("f164"), scale),
            "source": host,
            "error": None,
        }
    return {"pb": None, "pe_dynamic": None, "pe_ttm": None, "source": None, "error": last_error}


def load_pb_history(path: Path) -> list[float]:
    if not path.exists():
        return []
    values: list[float] = []
    with path.open(newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        fields = {name.lower(): name for name in (reader.fieldnames or [])}
        pb_key = fields.get("pb") or fields.get("pb_lf") or fields.get("pb-lf")
        if not pb_key:
            raise ValueError(f"{path} 缺少 pb / pb_lf 列")
        for row in reader:
            raw = (row.get(pb_key) or "").strip()
            if not raw:
                continue
            try:
                values.append(float(raw))
            except ValueError:
                continue
    return values


def third_friday(year: int, month: int) -> date:
    current = date(year, month, 1)
    friday_count = 0
    while current.month == month:
        if current.weekday() == 4:
            friday_count += 1
            if friday_count == 3:
                return current
        current += timedelta(days=1)
    raise ValueError(f"cannot find third Friday for {year}-{month}")


def spot_price(spot_code: str, today: date) -> float | None:
    start = today - timedelta(days=3)
    url = (
        "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?"
        + urllib.parse.urlencode({"param": f"{spot_code},day,{start.isoformat()},{today.isoformat()},500,qfq"})
    )
    data = json.loads(url_text(url, referer=f"{BASIS_HOME}/basis/"))
    qt = (((data.get("data") or {}).get(spot_code) or {}).get("qt") or {}).get(spot_code)
    if not qt:
        return None
    return float(qt[3])


def fetch_contracts(node: str) -> list[str]:
    url = f"{BASIS_HOME}/api/proxy/contracts?{urllib.parse.urlencode({'node': node})}"
    data = json.loads(url_text(url, referer=f"{BASIS_HOME}/basis/"))
    symbols = []
    for item in data:
        symbol = item.get("symbol") if isinstance(item, dict) else None
        if symbol and not re.match(r"^[A-Z]+0$", symbol):
            symbols.append(symbol)
    return symbols


def parse_sina_quotes(text: str) -> dict[str, list[str]]:
    quotes: dict[str, list[str]] = {}
    for line in text.splitlines():
        match = re.match(r'var hq_str_(\w+)="([^"]*)"', line)
        if match:
            quotes[match.group(1)] = match.group(2).split(",")
    return quotes


def roll_notice(days_left: int) -> dict[str, object]:
    if days_left < 0:
        return {
            "level": "expired",
            "message": "合约已过理论交割日，请确认是否已经完成移仓或平仓。",
        }
    if days_left <= ROLL_ALERT_DAYS:
        return {
            "level": "alert",
            "message": f"距离理论交割日仅 {days_left} 天，优先完成移仓检查。",
        }
    if days_left <= ROLL_WATCH_DAYS:
        return {
            "level": "watch",
            "message": f"距离理论交割日 {days_left} 天，进入移仓观察窗口。",
        }
    return {
        "level": "normal",
        "message": f"距离理论交割日 {days_left} 天，暂未进入移仓窗口。",
    }


def roll_summary(rows: list[dict]) -> dict[str, object] | None:
    live_rows = [row for row in rows if isinstance(row.get("days_left"), int) and row["days_left"] >= 0]
    if not live_rows:
        return None
    front = min(live_rows, key=lambda row: row["days_left"])
    notice = front.get("roll_notice") or roll_notice(front["days_left"])
    return {
        "contract": front.get("contract"),
        "delivery_date": front.get("delivery_date"),
        "days_left": front.get("days_left"),
        "level": notice.get("level"),
        "message": notice.get("message"),
        "watch_days": ROLL_WATCH_DAYS,
        "alert_days": ROLL_ALERT_DAYS,
        "calendar_note": "按第三个周五估算；若交易所节假日顺延，以中金所公告为准。",
    }


def fetch_basis() -> dict[str, dict]:
    today = date.today()
    contracts_by_type = {key: fetch_contracts(cfg["node"]) for key, cfg in BASIS_NODES.items()}
    all_contracts = [f"nf_{symbol}" for symbols in contracts_by_type.values() for symbol in symbols]
    if not all_contracts:
        return {}

    quote_url = f"{BASIS_HOME}/api/proxy/sina?{urllib.parse.urlencode({'list': ','.join(all_contracts)})}"
    quotes = parse_sina_quotes(url_text(quote_url, referer=f"{BASIS_HOME}/basis/"))
    result: dict[str, dict] = {}

    for key, cfg in BASIS_NODES.items():
        spot = spot_price(cfg["spot"], today)
        rows = []
        for symbol in contracts_by_type[key]:
            quote = quotes.get(f"nf_{symbol}")
            match = re.match(r"([A-Z]+)(\d{4})", symbol)
            if not quote or not match or spot is None:
                continue
            year = 2000 + int(match.group(2)[:2])
            month = int(match.group(2)[2:])
            delivery = third_friday(year, month)
            days_left = (delivery - today).days
            try:
                price = float(quote[3])
            except (ValueError, IndexError):
                continue
            basis = spot - price
            annualized = basis / price / days_left * 365 * 100 if days_left > 0 and price else None
            rows.append(
                {
                    "contract": symbol,
                    "spot": round(spot, 2),
                    "future": round(price, 2),
                    "basis": round(basis, 2),
                    "annualized_basis_pct": round(annualized, 2) if annualized is not None else None,
                    "days_left": days_left,
                    "delivery_date": delivery.isoformat(),
                    "roll_notice": roll_notice(days_left),
                }
            )
        rows.sort(key=lambda item: item["contract"])
        result[key] = {
            "source": f"{BASIS_HOME}/basis/",
            "formula": "basis = spot - future; annualized = basis / future / days_left * 365",
            "roll_rule": {
                "watch_days": ROLL_WATCH_DAYS,
                "alert_days": ROLL_ALERT_DAYS,
                "calendar_note": "按第三个周五估算；若交易所节假日顺延，以中金所公告为准。",
            },
            "roll_notice": roll_summary(rows),
            "contracts": rows,
        }
    return result


def latest_current_valuations() -> tuple[str, dict[str, dict]]:
    data = http_json("/data-service/indexValuation", {})
    if data.get("code") != "200":
        raise RuntimeError(f"CSI current valuation failed: {data}")
    payload = data.get("data") or {}
    trade_date = str(payload.get("tradeDate") or "")
    rows = payload.get("indexValuations") or []
    by_name = {row.get("indexName"): row for row in rows}
    return trade_date, by_name


def pe_history(code: str, start: str, end: str) -> tuple[list[float], dict]:
    data = http_json("/perf/indexCsiDsPe", {"indexCode": code, "startDate": start, "endDate": end})
    if data.get("code") != "200":
        return [], {}
    values = []
    rows = data.get("data") or []
    for row in rows:
        try:
            values.append(float(row.get("peg")))
        except (TypeError, ValueError):
            continue
    return values, (rows[-1] if rows else {})


def fetch_tushare_dailybasic_by_index(start: str, end: str) -> dict[str, list[dict[str, object]]]:
    try:
        client = TushareProClient.from_env()
    except Exception:
        return {}
    result: dict[str, list[dict[str, object]]] = {}
    for key, meta in INDEXES.items():
        try:
            rows = fetch_index_dailybasic(meta["code"], start, end, client=client)
        except Exception:
            rows = []
        if rows:
            result[key] = rows
    return result


def build_payload(history_dir: Path, years: int, include_basis: bool) -> dict:
    now = datetime.now(timezone.utc)
    end = now.strftime("%Y%m%d")
    start = (now - timedelta(days=365 * years + 10)).strftime("%Y%m%d")
    tushare_by_key = fetch_tushare_dailybasic_by_index(start, end)
    try:
        trade_date, current_by_name = latest_current_valuations()
    except Exception:
        if not tushare_by_key:
            raise
        current_by_name = {}
        trade_date = max(
            str(rows[-1].get("trade_date") or "")
            for rows in tushare_by_key.values()
            if rows
        )

    payload = {
        "schema_version": 1,
        "generated_at": now.isoformat(),
        "source": {
            "current_valuation": "Tushare Pro index_dailybasic; fallback 中证指数官网 /csindex-home/data-service/indexValuation",
            "current_pb_fallback": "东方财富 push2 quote fields f167/f152; best-effort only",
            "pe_history": "Tushare Pro index_dailybasic; fallback 中证指数官网 /csindex-home/perf/indexCsiDsPe",
            "pb_history": "Tushare Pro index_dailybasic; fallback local CSV",
            "basis": "xags.stephenslab.top/basis via its public proxy endpoints",
        },
        "strategy_rule": "PB percentile is the decision input; PE is auxiliary display only.",
        "trade_date": trade_date,
        "indexes": {},
    }

    basis_payload = fetch_basis() if include_basis else {}

    for key, meta in INDEXES.items():
        tushare_rows = tushare_by_key.get(key) or []
        tushare_latest = tushare_rows[-1] if tushare_rows else {}
        tushare_pe_values = [
            value
            for value in (
                number_or_none(row.get("pe_ttm")) or number_or_none(row.get("pe"))
                for row in tushare_rows
            )
            if value is not None
        ]
        tushare_pb_values = [
            value
            for value in (number_or_none(row.get("pb")) for row in tushare_rows)
            if value is not None
        ]
        current = current_by_name.get(meta["name"]) or {}
        pb = None
        pb_source = None
        if tushare_latest:
            pb = tushare_latest.get("pb")
            if not is_missing(pb):
                pb_source = "tushare_index_dailybasic"
        if is_missing(pb):
            pb = current.get("pb")
            if not is_missing(pb):
                pb_source = "csindex_current"
        fallback = {}
        if is_missing(pb):
            fallback = eastmoney_current_valuation(meta["code"])
            pb = fallback.get("pb")
            if not is_missing(pb):
                pb_source = "eastmoney_current_fallback"
        pe_values, pe_latest = (tushare_pe_values, {}) if tushare_pe_values else pe_history(meta["code"], start, end)
        pe = None
        pe_source = None
        if tushare_latest:
            pe = tushare_latest.get("pe_ttm") or tushare_latest.get("pe")
            if not is_missing(pe):
                pe_source = "tushare_index_dailybasic"
        if is_missing(pe) and pe_latest:
            pe = pe_latest.get("peg")
            if not is_missing(pe):
                pe_source = "csindex_pe_history"
        if is_missing(pe):
            pe = current.get("pe")
            if not is_missing(pe):
                pe_source = "csindex_current"
        if is_missing(pe) and fallback:
            pe = fallback.get("pe_ttm") or fallback.get("pe_dynamic")
            if not is_missing(pe):
                pe_source = "eastmoney_current_fallback"
        pb_values = tushare_pb_values or load_pb_history(history_dir / f"{meta['code']}_pb.csv")
        pe_num = number_or_none(pe)
        pb_num = number_or_none(pb)
        payload["indexes"][key] = {
            "code": meta["code"],
            "name": pe_latest.get("indexName") or meta["name"],
            "underlying_future": meta["underlying"],
            "trade_date": tushare_latest.get("trade_date") or pe_latest.get("tradeDate") or current.get("tradeDate") or trade_date,
            "pe": pe,
            "pb": pb,
            "pe_source": pe_source,
            "pb_source": pb_source,
            "eastmoney_fallback_error": fallback.get("error") if fallback and is_missing(pb) else None,
            "pe_percentile": percentile_rank(pe_values, pe_num) if pe_num is not None else None,
            "pb_percentile": percentile_rank(pb_values, pb_num) if pb_num is not None else None,
            "pb_percentile_source": "tushare_index_dailybasic" if tushare_pb_values and pb_num is not None else "local_csv" if pb_values and pb_num is not None else None,
            "pb_percentile_manual_required": not bool(pb_values) or pb_num is None,
            "history_window_years": years,
            "basis": basis_payload.get(key),
        }
    return payload


def main() -> int:
    parser = argparse.ArgumentParser(description="Update IC/IM valuation JSON for the dashboard.")
    parser.add_argument("--output", type=Path, default=DASHBOARD_JSON)
    parser.add_argument("--history-dir", type=Path, default=DEFAULT_HISTORY_DIR)
    parser.add_argument("--years", type=int, default=10)
    parser.add_argument("--include-basis", action=argparse.BooleanOptionalAction, default=True)
    args = parser.parse_args()

    payload = build_payload(args.history_dir, args.years, args.include_basis)
    conn = connect_market_data_db()
    run_id = start_sync_run(
        conn,
        "valuation",
        [meta["code"] for meta in INDEXES.values()],
        {"history_dir": str(args.history_dir), "years": args.years, "include_basis": args.include_basis},
    )
    import_valuation_payload(conn, payload, run_id)
    finish_sync_run(
        conn,
        run_id,
        "success",
        {"indexes": len(payload["indexes"]), "include_basis": args.include_basis},
    )
    payload = export_ic_im_valuation(conn)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_projection_record(conn, "ic-im-valuation", args.output, run_id, len(payload["indexes"]), {"include_basis": args.include_basis})
    print(f"wrote {args.output}")
    for key, item in payload["indexes"].items():
        pbp = item["pb_percentile"]
        basis_count = len(((item.get("basis") or {}).get("contracts") or []))
        print(
            f"{key} {item['name']} PE={item['pe']} PB={item['pb']} "
            f"PB_pct={pbp if pbp is not None else 'manual'} basis_contracts={basis_count}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
