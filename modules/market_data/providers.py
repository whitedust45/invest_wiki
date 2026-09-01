"""Dashboard market data providers.

The module keeps the existing JSON contracts stable while centralizing source
selection. Official Tushare Pro HTTP is preferred for A-share and ETF data;
the previous Sina/Tencent/Yahoo sources remain as fallbacks.
"""

from __future__ import annotations

import json
import math
import os
import re
import importlib
import time
import urllib.parse
import urllib.request
from collections import deque
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable


ROOT = Path(__file__).resolve().parents[2]
SYNC_ENV_FILE = ROOT / "services" / "sync" / ".env"
TUSHARE_API_URL = "https://api.tushare.pro"


class MarketDataError(RuntimeError):
    """Market data fetch failed."""


class NoopRateLimiter:
    def wait(self) -> None:
        return


class SlidingWindowRateLimiter:
    def __init__(self, max_per_minute: int = 180):
        self.max_per_minute = max(1, int(max_per_minute))
        self.window: deque[float] = deque()

    def wait(self) -> None:
        now = time.monotonic()
        while self.window and now - self.window[0] > 60:
            self.window.popleft()
        if len(self.window) >= self.max_per_minute:
            sleep_for = 60 - (now - self.window[0]) + 0.05
            if sleep_for > 0:
                time.sleep(sleep_for)
            now = time.monotonic()
            while self.window and now - self.window[0] > 60:
                self.window.popleft()
        self.window.append(time.monotonic())


def read_env_file(path: Path) -> dict[str, str]:
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


def load_sync_env(root: Path | str = ROOT) -> dict[str, str]:
    env_path = Path(root) / "services" / "sync" / ".env"
    config = read_env_file(env_path)
    for key, value in config.items():
        os.environ.setdefault(key, value)
    return config


def market_code(symbol: str) -> str | None:
    code = symbol.strip()
    if not re.fullmatch(r"\d{6}", code):
        return None
    if re.match(r"^(000|001|002|003|159|300|301)", code):
        return f"sz{code}"
    if re.match(r"^(510|511|512|513|515|516|517|518|519|520|560|561|562|563|588|600|601|603|605|688|689)", code):
        return f"sh{code}"
    return None


def is_us_symbol(symbol: str) -> bool:
    code = symbol.strip().upper()
    return market_code(code) is None and re.fullmatch(r"[A-Z][A-Z0-9.-]{0,9}", code) is not None


def ts_code_for_symbol(symbol: str) -> str:
    code = symbol.strip()
    market = market_code(code)
    if not market:
        raise MarketDataError(f"{symbol} is not a supported A-share/ETF symbol")
    suffix = "SZ" if market.startswith("sz") else "SH"
    return f"{code}.{suffix}"


def is_fund_symbol(symbol: str) -> bool:
    code = symbol.strip()
    return bool(re.match(r"^(159|5)", code))


def tushare_daily_api(symbol: str) -> str:
    return "fund_daily" if is_fund_symbol(symbol) else "daily"


def normalize_tushare_date(value: str | None) -> str:
    if not value:
        return ""
    text = str(value).strip()
    if re.fullmatch(r"\d{8}", text):
        return text
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", text):
        return text.replace("-", "")
    return text


def iso_date(value: object) -> str:
    text = str(value or "")
    if re.fullmatch(r"\d{8}", text):
        return f"{text[:4]}-{text[4:6]}-{text[6:8]}"
    return text


def number_or_none(value: object) -> float | None:
    if value is None or value == "" or value == "-":
        return None
    try:
        num = float(value)
    except (TypeError, ValueError):
        return None
    return num if math.isfinite(num) else None


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


class TushareProClient:
    def __init__(
        self,
        token: str | None = None,
        api_url: str = TUSHARE_API_URL,
        urlopen: Callable[..., Any] = urllib.request.urlopen,
        rate_limiter: Any | None = None,
    ):
        self.token = token or os.environ.get("TUSHARE_TOKEN", "")
        if not self.token:
            raise MarketDataError("TUSHARE_TOKEN is not configured")
        self.api_url = api_url
        self.urlopen = urlopen
        self.rate_limiter = rate_limiter or SlidingWindowRateLimiter(int(os.environ.get("TUSHARE_RPM", "180")))

    @classmethod
    def from_env(cls) -> "TushareProClient":
        load_sync_env()
        return cls()

    def query(self, api_name: str, params: dict[str, object] | None = None, fields: str = "") -> list[dict[str, object]]:
        self.rate_limiter.wait()
        body = {
            "api_name": api_name,
            "token": self.token,
            "params": params or {},
            "fields": fields,
        }
        req = urllib.request.Request(
            self.api_url,
            data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json", "User-Agent": "invest-wiki-dashboard"},
            method="POST",
        )
        with self.urlopen(req, timeout=20) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
        if payload.get("code") != 0:
            raise MarketDataError(f"Tushare {api_name} failed: {payload.get('msg') or payload.get('code')}")
        data = payload.get("data") or {}
        fields_list = data.get("fields") or []
        rows = data.get("items") or []
        return [dict(zip(fields_list, row)) for row in rows]


class TushareSdkClient:
    def __init__(self, token: str | None = None, sdk_module: Any | None = None, rate_limiter: Any | None = None):
        self.token = token or os.environ.get("TUSHARE_TOKEN", "")
        if not self.token:
            raise MarketDataError("TUSHARE_TOKEN is not configured")
        self.sdk_module = sdk_module or importlib.import_module("tushare")
        self.pro = self.sdk_module.pro_api(self.token)
        self.rate_limiter = rate_limiter or SlidingWindowRateLimiter(int(os.environ.get("TUSHARE_RPM", "180")))

    @classmethod
    def from_env(cls) -> "TushareSdkClient":
        load_sync_env()
        return cls()

    def query(self, api_name: str, params: dict[str, object] | None = None, fields: str = "") -> list[dict[str, object]]:
        self.rate_limiter.wait()
        params = params or {}
        try:
            frame = self.pro.query(api_name, **params, fields=fields)
        except TypeError:
            frame = self.pro.query(api_name, **params)
        if hasattr(frame, "to_dict"):
            return frame.to_dict(orient="records")
        if isinstance(frame, list):
            return [dict(row) for row in frame]
        raise MarketDataError(f"Tushare SDK {api_name} returned unsupported payload")


def create_tushare_client_from_env(prefer_sdk: bool = True) -> Any:
    load_sync_env()
    if prefer_sdk:
        try:
            return TushareSdkClient()
        except ModuleNotFoundError:
            pass
    return TushareProClient()


class TushareMarketDataProvider:
    DAILY_FIELDS = "ts_code,trade_date,open,high,low,close,pre_close,change,pct_chg,vol,amount"
    ADJ_FIELDS = "ts_code,trade_date,adj_factor"

    def __init__(self, client: TushareProClient):
        self.client = client

    @classmethod
    def from_env(cls) -> "TushareMarketDataProvider":
        return cls(create_tushare_client_from_env())

    def _daily_rows(self, symbol: str, start_date: str, end_date: str) -> list[dict[str, object]]:
        ts_code = ts_code_for_symbol(symbol)
        return self.client.query(
            tushare_daily_api(symbol),
            {"ts_code": ts_code, "start_date": start_date, "end_date": end_date},
            self.DAILY_FIELDS,
        )

    def _adj_factors(self, symbol: str, start_date: str, end_date: str) -> dict[str, float]:
        if is_fund_symbol(symbol):
            return {}
        ts_code = ts_code_for_symbol(symbol)
        rows = self.client.query(
            "adj_factor",
            {"ts_code": ts_code, "start_date": start_date, "end_date": end_date},
            self.ADJ_FIELDS,
        )
        result: dict[str, float] = {}
        for row in rows:
            trade_date = str(row.get("trade_date") or "")
            factor = number_or_none(row.get("adj_factor"))
            if trade_date and factor:
                result[trade_date] = factor
        return result

    def fetch_daily_history(self, symbol: str, count: int, end_date: str = "") -> list[dict[str, object]]:
        end = normalize_tushare_date(end_date) or date.today().strftime("%Y%m%d")
        end_dt = datetime.strptime(end, "%Y%m%d").date()
        start = (end_dt - timedelta(days=max(count * 2 + 30, 45))).strftime("%Y%m%d")
        rows = self._daily_rows(symbol, start, end)
        if not rows:
            raise MarketDataError(f"Tushare has no daily rows for {symbol}")
        rows.sort(key=lambda item: str(item.get("trade_date") or ""))

        factors: dict[str, float] = {}
        try:
            factors = self._adj_factors(symbol, start, end)
        except Exception:
            factors = {}
        latest_factor = factors.get(str(rows[-1].get("trade_date") or ""))

        result: list[dict[str, object]] = []
        for row in rows[-count:]:
            factor = factors.get(str(row.get("trade_date") or ""))
            scale = factor / latest_factor if factor and latest_factor else 1.0

            def price(field: str) -> float:
                value = number_or_none(row.get(field))
                return round((value or 0.0) * scale, 4)

            result.append(
                {
                    "date": iso_date(row.get("trade_date")),
                    "open": price("open"),
                    "close": price("close"),
                    "high": price("high"),
                    "low": price("low"),
                    "volume": number_or_none(row.get("vol")) or 0.0,
                    "source": "Tushare前复权日线" if factors else "Tushare日线",
                }
            )
        return result

    def fetch_quote(self, symbol: str) -> dict[str, object]:
        rows = self.fetch_daily_history(symbol, count=10)
        latest = rows[-1]
        return {
            "symbol": symbol,
            "name": symbol,
            "price": latest["close"],
            "source": latest["source"],
            "trade_time": latest["date"],
        }


class FreeMarketDataProvider:
    def __init__(self):
        self._usd_cny: float | None = None

    def fetch_sina(self, symbol: str) -> dict[str, object]:
        code = market_code(symbol)
        if not code:
            raise MarketDataError(f"{symbol} is not a supported A-share/ETF symbol")
        text = url_text(f"https://hq.sinajs.cn/list={code}", referer="https://finance.sina.com.cn/")
        match = re.search(rf'var hq_str_{code}="([^"]*)"', text)
        if not match:
            raise MarketDataError("Sina quote not found")
        fields = match.group(1).split(",")
        price = float(fields[3])
        if price <= 0:
            raise MarketDataError("Sina quote price is unavailable")
        trade_time = " ".join(part for part in (fields[30] if len(fields) > 30 else "", fields[31] if len(fields) > 31 else "") if part)
        return {
            "symbol": symbol,
            "name": fields[0] or symbol,
            "price": price,
            "source": "新浪A股",
            "trade_time": trade_time,
        }

    def fetch_tencent(self, symbol: str) -> dict[str, object]:
        code = market_code(symbol)
        if not code:
            raise MarketDataError(f"{symbol} is not a supported A-share/ETF symbol")
        text = url_text(f"https://qt.gtimg.cn/q={code}", referer="https://gu.qq.com/")
        match = re.search(rf'v_{code}="([^"]*)"', text)
        if not match:
            raise MarketDataError("Tencent quote not found")
        fields = match.group(1).split("~")
        price = float(fields[3])
        if price <= 0:
            raise MarketDataError("Tencent quote price is unavailable")
        return {
            "symbol": symbol,
            "name": fields[1] or symbol,
            "price": price,
            "source": "腾讯A股",
            "trade_time": fields[30] if len(fields) > 30 else "",
        }

    def fetch_tencent_daily_history(self, symbol: str, count: int, end_date: str = "") -> list[dict[str, object]]:
        code = market_code(symbol)
        if not code:
            raise MarketDataError(f"{symbol} is not a supported A-share/ETF symbol")
        url = (
            "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?"
            f"param={urllib.parse.quote(f'{code},day,,{end_date},{count},qfq')}"
        )
        payload = json.loads(url_text(url, referer="https://gu.qq.com/"))
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

    def fetch_yahoo_latest(self, symbol: str) -> dict[str, object]:
        code = symbol.strip().upper()
        url = f"https://query1.finance.yahoo.com/v8/finance/chart/{urllib.parse.quote(code)}?range=10d&interval=1d"
        payload = url_json(url, referer="https://finance.yahoo.com/")
        result = (payload.get("chart", {}).get("result") or [None])[0]
        if not result:
            raise MarketDataError("Yahoo chart data not found")
        meta = result.get("meta", {})
        timestamps = result.get("timestamp") or []
        quote = ((result.get("indicators") or {}).get("quote") or [None])[0] or {}
        closes = quote.get("close") or []
        latest = None
        for timestamp, close in zip(timestamps, closes):
            if close is not None:
                latest = (int(timestamp), float(close))
        if latest is None or latest[1] <= 0:
            raise MarketDataError("Yahoo close price is unavailable")
        return {
            "symbol": code,
            "name": meta.get("shortName") or meta.get("symbol") or code,
            "price": latest[1],
            "currency": meta.get("currency") or "",
            "trade_time": datetime.fromtimestamp(latest[0], timezone.utc).date().isoformat(),
        }

    def fetch_usd_cny(self) -> float:
        if self._usd_cny is None:
            quote = self.fetch_yahoo_latest("CNY=X")
            price = float(quote["price"])
            if price <= 0:
                raise MarketDataError("USD/CNY is unavailable")
            self._usd_cny = price
        return self._usd_cny

    def fetch_us_quote(self, symbol: str, usd_cny: float | None = None) -> dict[str, object]:
        quote = self.fetch_yahoo_latest(symbol)
        raw_price = float(quote["price"])
        fx = usd_cny if usd_cny is not None else self.fetch_usd_cny()
        return {
            "symbol": symbol.strip().upper(),
            "name": quote["name"],
            "price": round(raw_price * fx, 4),
            "raw_price": raw_price,
            "raw_currency": quote.get("currency") or "USD",
            "usd_cny": fx,
            "source": "Yahoo美股人民币折算",
            "trade_time": quote["trade_time"],
        }

    def fetch_quote(self, symbol: str, usd_cny: float | None = None) -> dict[str, object]:
        if is_us_symbol(symbol):
            return self.fetch_us_quote(symbol, usd_cny)
        try:
            return self.fetch_sina(symbol)
        except Exception as sina_error:
            quote = self.fetch_tencent(symbol)
            quote["fallback_reason"] = str(sina_error)
            return quote

    def fetch_daily_history(self, symbol: str, count: int, end_date: str = "") -> list[dict[str, object]]:
        return self.fetch_tencent_daily_history(symbol, count, end_date=end_date)


class CompositeMarketDataProvider:
    def __init__(self, tushare_provider: Any | None = None, fallback_provider: Any | None = None):
        self.tushare_provider = tushare_provider
        self.fallback_provider = fallback_provider or FreeMarketDataProvider()

    @classmethod
    def from_env(cls) -> "CompositeMarketDataProvider":
        load_sync_env()
        try:
            tushare_provider = TushareMarketDataProvider.from_env()
        except Exception:
            tushare_provider = None
        return cls(tushare_provider=tushare_provider)

    def fetch_quote(self, symbol: str) -> dict[str, object]:
        if is_us_symbol(symbol):
            return self.fallback_provider.fetch_quote(symbol)
        if self.tushare_provider is not None and market_code(symbol):
            try:
                return self.tushare_provider.fetch_quote(symbol)
            except Exception as error:
                quote = self.fallback_provider.fetch_quote(symbol)
                quote["fallback_reason"] = f"Tushare: {error}"
                return quote
        return self.fallback_provider.fetch_quote(symbol)

    def fetch_daily_history(self, symbol: str, count: int, end_date: str = "") -> list[dict[str, object]]:
        if not market_code(symbol):
            return []
        if self.tushare_provider is not None:
            try:
                return self.tushare_provider.fetch_daily_history(symbol, count, end_date=end_date)
            except Exception:
                pass
        return self.fallback_provider.fetch_daily_history(symbol, count, end_date=end_date)


def build_quotes_payload(symbols: list[str], provider: CompositeMarketDataProvider | None = None) -> dict[str, object]:
    provider = provider or CompositeMarketDataProvider.from_env()
    quotes: dict[str, dict[str, object]] = {}
    errors: dict[str, str] = {}
    for raw_symbol in symbols:
        symbol = raw_symbol.strip()
        if not symbol:
            continue
        try:
            quotes[symbol] = provider.fetch_quote(symbol)
        except Exception as error:
            errors[symbol] = str(error)
    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": {
            "name": "dashboard-market-data",
            "primary": "Tushare Pro official HTTP API",
            "fallback": "Sina hq.sinajs.cn / Tencent qt.gtimg.cn",
            "us": "Yahoo finance chart, converted by USD/CNY",
            "reference": "https://github.com/lululu811/zettaranc-skill/tree/main",
        },
        "quotes": quotes,
        "errors": errors,
    }


def build_history_payload(
    symbols: list[str],
    days: int,
    end_date: str,
    provider: CompositeMarketDataProvider | None = None,
) -> dict[str, object]:
    provider = provider or CompositeMarketDataProvider.from_env()
    histories: dict[str, list[dict[str, object]]] = {}
    errors: dict[str, str] = {}
    for raw_symbol in symbols:
        symbol = raw_symbol.strip()
        if not symbol or not market_code(symbol):
            continue
        try:
            histories[symbol] = provider.fetch_daily_history(symbol, days, end_date=end_date)
        except Exception as error:
            errors[symbol] = str(error)
    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": {
            "name": "dashboard-market-data daily close",
            "primary": "Tushare Pro official HTTP API",
            "fallback": "Tencent appstock/app/fqkline/get",
            "reference": "https://github.com/lululu811/zettaranc-skill/tree/main",
            "adjustment": "qfq",
        },
        "histories": histories,
        "errors": errors,
    }


def fetch_index_dailybasic(code: str, start_date: str, end_date: str, client: TushareProClient | None = None) -> list[dict[str, object]]:
    client = client or TushareProClient.from_env()
    rows = client.query(
        "index_dailybasic",
        {"ts_code": f"{code}.SH", "start_date": start_date, "end_date": end_date},
        "ts_code,trade_date,pe,pe_ttm,pb",
    )
    rows.sort(key=lambda item: str(item.get("trade_date") or ""))
    return rows
