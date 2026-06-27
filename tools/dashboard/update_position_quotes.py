#!/usr/bin/env python3
"""Fetch latest prices for the holding valuation layer.

The source strategy follows the same practical shape as mpquant/Ashare:
try Sina first and fall back to Tencent for A-shares. US tickers are fetched
from Yahoo chart data and converted to CNY with USD/CNY. The script writes a
static JSON file that the dashboard can load without requiring a backend service.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


DASHBOARD_JSON = Path("apps/dashboard/data/position-quotes.json")
POSITION_HISTORY_JSON = Path("apps/dashboard/data/position-history.json")


def market_code(symbol: str) -> str | None:
    code = symbol.strip()
    if not re.fullmatch(r"\d{6}", code):
        return None
    if re.match(r"^(000|001|002|003|300|301)", code):
        return f"sz{code}"
    if re.match(r"^(600|601|603|605|688|689)", code):
        return f"sh{code}"
    return None


def is_us_symbol(symbol: str) -> bool:
    code = symbol.strip().upper()
    return market_code(code) is None and re.fullmatch(r"[A-Z][A-Z0-9.-]{0,9}", code) is not None


def url_text(url: str, referer: str) -> str:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": referer,
            "Accept": "*/*",
        },
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        raw = resp.read()
    for encoding in ("gb18030", "utf-8"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", "ignore")


def url_json(url: str, referer: str) -> dict[str, object]:
    return json.loads(url_text(url, referer=referer))


def fetch_sina(symbol: str) -> dict[str, object]:
    code = market_code(symbol)
    if not code:
        raise ValueError(f"{symbol} is not a supported A-share symbol")
    url = f"https://hq.sinajs.cn/list={code}"
    text = url_text(url, referer="https://finance.sina.com.cn/")
    match = re.search(rf'var hq_str_{code}="([^"]*)"', text)
    if not match:
        raise ValueError("Sina quote not found")
    fields = match.group(1).split(",")
    price = float(fields[3])
    if price <= 0:
        raise ValueError("Sina quote price is unavailable")
    trade_time = " ".join(part for part in (fields[30] if len(fields) > 30 else "", fields[31] if len(fields) > 31 else "") if part)
    return {
        "symbol": symbol,
        "name": fields[0] or symbol,
        "price": price,
        "source": "新浪A股",
        "trade_time": trade_time,
    }


def fetch_tencent(symbol: str) -> dict[str, object]:
    code = market_code(symbol)
    if not code:
        raise ValueError(f"{symbol} is not a supported A-share symbol")
    url = f"https://qt.gtimg.cn/q={code}"
    text = url_text(url, referer="https://gu.qq.com/")
    match = re.search(rf'v_{code}="([^"]*)"', text)
    if not match:
        raise ValueError("Tencent quote not found")
    fields = match.group(1).split("~")
    price = float(fields[3])
    if price <= 0:
        raise ValueError("Tencent quote price is unavailable")
    return {
        "symbol": symbol,
        "name": fields[1] or symbol,
        "price": price,
        "source": "腾讯A股",
        "trade_time": fields[30] if len(fields) > 30 else "",
    }


def fetch_tencent_daily_history(symbol: str, count: int, end_date: str = "") -> list[dict[str, object]]:
    code = market_code(symbol)
    if not code:
        raise ValueError(f"{symbol} is not a supported A-share symbol")
    url = (
        "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?"
        f"param={urllib.parse.quote(f'{code},day,,{end_date},{count},qfq')}"
    )
    text = url_text(url, referer="https://gu.qq.com/")
    payload = json.loads(text)
    data = payload.get("data", {}).get(code, {})
    rows = data.get("qfqday") or data.get("day") or []
    result = []
    for row in rows:
        if len(row) < 6:
            continue
        result.append(
            {
                "date": row[0],
                "open": float(row[1]),
                "close": float(row[2]),
                "high": float(row[3]),
                "low": float(row[4]),
                "volume": float(row[5]),
                "source": "腾讯日线",
            }
        )
    return result


def fetch_yahoo_latest(symbol: str) -> dict[str, object]:
    code = symbol.strip().upper()
    url = f"https://query1.finance.yahoo.com/v8/finance/chart/{urllib.parse.quote(code)}?range=10d&interval=1d"
    payload = url_json(url, referer="https://finance.yahoo.com/")
    result = (payload.get("chart", {}).get("result") or [None])[0]
    if not result:
        raise ValueError("Yahoo chart data not found")
    meta = result.get("meta", {})
    timestamps = result.get("timestamp") or []
    quote = ((result.get("indicators") or {}).get("quote") or [None])[0] or {}
    closes = quote.get("close") or []
    latest = None
    for timestamp, close in zip(timestamps, closes):
        if close is None:
            continue
        latest = (int(timestamp), float(close))
    if latest is None or latest[1] <= 0:
        raise ValueError("Yahoo close price is unavailable")
    return {
        "symbol": code,
        "name": meta.get("shortName") or meta.get("symbol") or code,
        "price": latest[1],
        "currency": meta.get("currency") or "",
        "trade_time": datetime.fromtimestamp(latest[0], timezone.utc).date().isoformat(),
    }


def fetch_usd_cny() -> float:
    quote = fetch_yahoo_latest("CNY=X")
    price = float(quote["price"])
    if price <= 0:
        raise ValueError("USD/CNY is unavailable")
    return price


def fetch_us_quote(symbol: str, usd_cny: float) -> dict[str, object]:
    quote = fetch_yahoo_latest(symbol)
    raw_price = float(quote["price"])
    return {
        "symbol": symbol.strip().upper(),
        "name": quote["name"],
        "price": round(raw_price * usd_cny, 4),
        "raw_price": raw_price,
        "raw_currency": quote.get("currency") or "USD",
        "usd_cny": usd_cny,
        "source": "Yahoo美股人民币折算",
        "trade_time": quote["trade_time"],
    }


def fetch_quote(symbol: str, usd_cny: float | None = None) -> tuple[dict[str, object] | None, str | None]:
    if is_us_symbol(symbol):
        try:
            return fetch_us_quote(symbol, usd_cny if usd_cny is not None else fetch_usd_cny()), None
        except Exception as us_error:  # noqa: BLE001
            return None, str(us_error)

    try:
        return fetch_sina(symbol), None
    except Exception as sina_error:  # noqa: BLE001 - fallback source must not stop the whole run
        try:
            quote = fetch_tencent(symbol)
            quote["fallback_reason"] = str(sina_error)
            return quote, None
        except Exception as tencent_error:  # noqa: BLE001
            return None, f"{sina_error}; {tencent_error}"


def build_payload(symbols: list[str]) -> dict[str, object]:
    quotes: dict[str, dict[str, object]] = {}
    errors: dict[str, str] = {}
    usd_cny: float | None = None
    if any(is_us_symbol(symbol) for symbol in symbols):
        try:
            usd_cny = fetch_usd_cny()
        except Exception as error:  # noqa: BLE001
            errors["USD/CNY"] = str(error)
    for raw_symbol in symbols:
        symbol = raw_symbol.strip()
        if not symbol:
            continue
        quote, error = fetch_quote(symbol, usd_cny)
        if quote:
            quotes[symbol] = quote
        elif error:
            errors[symbol] = error
    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": {
            "name": "Ashare-compatible",
            "primary": "Sina hq.sinajs.cn",
            "fallback": "Tencent qt.gtimg.cn",
            "us": "Yahoo finance chart, converted by USD/CNY",
            "reference": "https://github.com/mpquant/Ashare",
        },
        "quotes": quotes,
        "errors": errors,
    }


def build_history_payload(symbols: list[str], days: int, end_date: str) -> dict[str, object]:
    histories: dict[str, list[dict[str, object]]] = {}
    errors: dict[str, str] = {}
    for raw_symbol in symbols:
        symbol = raw_symbol.strip()
        if not symbol:
            continue
        if not market_code(symbol):
            continue
        try:
            histories[symbol] = fetch_tencent_daily_history(symbol, days, end_date=end_date)
        except Exception as error:  # noqa: BLE001 - one failed symbol should not stop the whole run
            errors[symbol] = str(error)
    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": {
            "name": "Ashare-compatible daily close",
            "primary": "Tencent appstock/app/fqkline/get",
            "reference": "https://github.com/mpquant/Ashare",
            "adjustment": "qfq",
        },
        "histories": histories,
        "errors": errors,
    }


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate dashboard A-share quote JSON.")
    parser.add_argument("symbols", nargs="*", help="A-share symbols, e.g. 000568 000858")
    parser.add_argument("--output", default=str(DASHBOARD_JSON), help="Output JSON path")
    parser.add_argument("--history-days", type=int, default=0, help="Also write daily close history for N trading records")
    parser.add_argument("--history-output", default=str(POSITION_HISTORY_JSON), help="Daily close history JSON path")
    parser.add_argument("--end-date", default="", help="History end date, YYYY-MM-DD. Empty means latest.")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if not args.symbols:
        print("missing symbols, e.g. tools/dashboard/update_position_quotes.py 000568 000858", file=sys.stderr)
        return 2
    payload = build_payload(args.symbols)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {output} with {len(payload['quotes'])} quotes, {len(payload['errors'])} errors")
    if args.history_days > 0:
        history_payload = build_history_payload(args.symbols, args.history_days, args.end_date)
        history_output = Path(args.history_output)
        history_output.parent.mkdir(parents=True, exist_ok=True)
        history_output.write_text(json.dumps(history_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(
            f"wrote {history_output} with "
            f"{sum(len(rows) for rows in history_payload['histories'].values())} rows, "
            f"{len(history_payload['errors'])} errors"
        )
    return 0 if payload["quotes"] else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
