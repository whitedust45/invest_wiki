"""Pure daily-bar signals for the A-share tail strategy suite."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import math
from pathlib import Path
import sys
from typing import Any, Sequence

from modules.short_term.strategies.brick import DailyBar, fetch_board_bars, load_tq


MAX_POSITIONS = 3
MIN_HISTORY_BARS = 65


@dataclass(frozen=True)
class TailCandidate:
    """One tail-entry candidate and the exit policy attached to it."""

    strategy_id: str
    symbol: str
    score: float
    metrics: dict[str, float]
    stop_loss_pct: float
    take_profit_pct: float
    max_holding_days: int
    exit_rule: str


@dataclass(frozen=True)
class TailStrategy:
    """Static definition displayed by the short-term strategy registry."""

    strategy_id: str
    display_name: str
    description: str


def sma(values: Sequence[float], period: int) -> list[float]:
    """Return a progressive simple moving average for every input value."""
    if period <= 0:
        raise ValueError("均线周期必须大于 0")
    result: list[float] = []
    for index in range(len(values)):
        window = values[max(0, index - period + 1) : index + 1]
        result.append(sum(window) / len(window))
    return result


def ema(values: Sequence[float], period: int) -> list[float]:
    """Return an exponential moving average seeded by the first observation."""
    if period <= 0:
        raise ValueError("均线周期必须大于 0")
    result: list[float] = []
    multiplier = 2.0 / (period + 1.0)
    for value in values:
        result.append(float(value) if not result else (value - result[-1]) * multiplier + result[-1])
    return result


def rsi(values: Sequence[float], period: int = 14) -> list[float]:
    """Return a progressive Wilder-style RSI, using 50 for flat observations."""
    if period <= 0:
        raise ValueError("RSI 周期必须大于 0")
    if not values:
        return []
    result = [50.0]
    gains: list[float] = []
    losses: list[float] = []
    for previous, current in zip(values, values[1:]):
        delta = float(current) - float(previous)
        gains.append(max(delta, 0.0))
        losses.append(max(-delta, 0.0))
        window_gains = gains[-period:]
        window_losses = losses[-period:]
        average_gain = sum(window_gains) / len(window_gains)
        average_loss = sum(window_losses) / len(window_losses)
        if average_gain == 0 and average_loss == 0:
            result.append(50.0)
        elif average_loss == 0:
            result.append(100.0)
        else:
            relative_strength = average_gain / average_loss
            result.append(100.0 - 100.0 / (1.0 + relative_strength))
    return result


def macd(
    values: Sequence[float], fast_period: int = 12, slow_period: int = 26, signal_period: int = 9
) -> dict[str, list[float]]:
    """Return MACD DIF, DEA and histogram series with conventional parameters."""
    fast = ema(values, fast_period)
    slow = ema(values, slow_period)
    dif = [fast_value - slow_value for fast_value, slow_value in zip(fast, slow)]
    dea = ema(dif, signal_period)
    return {"dif": dif, "dea": dea, "hist": [value - signal for value, signal in zip(dif, dea)]}


def has_bullish_divergence(closes: Sequence[float], dif_values: Sequence[float]) -> bool:
    """Detect two successive price troughs with a higher second MACD DIF trough."""
    if len(closes) != len(dif_values) or len(closes) < 6:
        return False
    split = len(closes) // 2
    first_index = min(range(split), key=lambda index: closes[index])
    second_index = min(range(split, len(closes)), key=lambda index: closes[index])
    return closes[second_index] < closes[first_index] and dif_values[second_index] > dif_values[first_index]


def is_cup_handle_breakout(closes: Sequence[float], amounts: Sequence[float]) -> bool:
    """Recognize a compact cup-and-handle breakout using daily close and amount."""
    if len(closes) != len(amounts) or len(closes) < 14:
        return False
    handle_size = max(3, len(closes) // 4)
    cup_closes = closes[:-handle_size]
    handle_closes = closes[-handle_size:]
    cup_amounts = amounts[:-handle_size]
    handle_amounts = amounts[-handle_size:]
    if not cup_closes or not handle_closes or min(cup_closes) <= 0:
        return False
    rim = max(cup_closes)
    cup_bottom = min(cup_closes)
    midline = (rim + cup_bottom) / 2.0
    recovered_near_rim = cup_closes[-1] >= rim * 0.95
    handle_stays_high = min(handle_closes[:-1]) >= midline if len(handle_closes) > 1 else False
    breakout = handle_closes[-1] > max(handle_closes[:-1])
    cup_average_amount = sum(cup_amounts) / len(cup_amounts)
    handle_average_amount = sum(handle_amounts) / len(handle_amounts)
    return recovered_near_rim and handle_stays_high and breakout and handle_average_amount < cup_average_amount


def available_tail_strategies() -> list[TailStrategy]:
    """Return the four source-derived strategy families in stable CLI order."""
    return [
        TailStrategy("steady_momentum", "稳步上涨低波动", "低波动趋势延续尾盘候选。"),
        TailStrategy("trend_confirmation", "趋势确认择时", "市场多头下的趋势确认尾盘候选。"),
        TailStrategy("macd_divergence", "MACD 底背驰反转", "市场多头下的底背驰尾盘候选。"),
        TailStrategy("cup_handle_breakout", "杯柄突破", "缩量杯柄向上突破的尾盘候选。"),
    ]


def select_top_candidates(
    candidates: Sequence[TailCandidate], limit: int = MAX_POSITIONS
) -> list[TailCandidate]:
    """Return deterministic positive-score execution candidates, capped at three."""
    if limit <= 0:
        return []
    capped_limit = min(limit, MAX_POSITIONS)
    return sorted(
        (item for item in candidates if item.score > 0),
        key=lambda item: (-item.score, item.symbol),
    )[:capped_limit]


def score_strategy(
    strategy_id: str,
    symbol: str,
    bars: Sequence[DailyBar],
    market_bars: Sequence[DailyBar] | None = None,
) -> TailCandidate | None:
    """Score one strategy after its common daily-bar warm-up requirement."""
    known_ids = {item.strategy_id for item in available_tail_strategies()}
    if strategy_id not in known_ids:
        raise ValueError(f"未知尾盘策略: {strategy_id}")
    if len(bars) < MIN_HISTORY_BARS:
        return None
    if strategy_id in {"trend_confirmation", "macd_divergence"} and (
        market_bars is None or len(market_bars) < MIN_HISTORY_BARS
    ):
        return None
    if strategy_id == "steady_momentum":
        return _score_steady_momentum(symbol, bars)
    if strategy_id == "trend_confirmation":
        return _score_trend_confirmation(symbol, bars, market_bars or ())
    if strategy_id == "macd_divergence":
        return _score_macd_divergence(symbol, bars, market_bars or ())
    if strategy_id == "cup_handle_breakout":
        return _score_cup_handle_breakout(symbol, bars)
    return None


def _score_steady_momentum(symbol: str, bars: Sequence[DailyBar]) -> TailCandidate | None:
    """Score a stable, low-amplitude uptrend from the latest daily bars."""
    closes = [bar.close for bar in bars]
    amounts = [bar.amount for bar in bars]
    ma10 = sma(closes, 10)[-1]
    ma20 = sma(closes, 20)[-1]
    if ma10 <= 0 or ma20 <= 0 or closes[-1] <= ma10 or ma10 <= ma20:
        return None
    returns = [closes[index] / closes[index - 1] - 1.0 for index in range(-9, 0)]
    average_return = sum(returns) / len(returns)
    variance = sum((value - average_return) ** 2 for value in returns) / len(returns)
    return_volatility = math.sqrt(variance)
    sharpe_like = average_return / return_volatility if return_volatility > 0 else average_return * 100.0
    amplitudes = [(bar.high - bar.low) / bar.close for bar in bars[-10:] if bar.close > 0]
    average_amplitude = sum(amplitudes) / len(amplitudes) if amplitudes else 1.0
    recent_amounts = amounts[-10:]
    mean_amount = sum(recent_amounts) / len(recent_amounts)
    amount_variance = sum((value - mean_amount) ** 2 for value in recent_amounts) / len(recent_amounts)
    amount_cv = math.sqrt(amount_variance) / mean_amount if mean_amount > 0 else math.inf
    if average_return <= 0 or sharpe_like <= 0 or average_amplitude > 0.08 or amount_cv > 0.8:
        return None
    score = sharpe_like * 100.0 + average_return * 1_000.0 + max(0.0, 8.0 - average_amplitude * 100.0)
    return TailCandidate(
        strategy_id="steady_momentum",
        symbol=symbol,
        score=score,
        metrics={
            "sharpe_like": sharpe_like,
            "average_return_10d": average_return,
            "average_amplitude_10d": average_amplitude,
            "amount_cv_10d": amount_cv,
        },
        stop_loss_pct=-0.04,
        take_profit_pct=0.08,
        max_holding_days=10,
        exit_rule="close_below_ma5",
    )


def _score_trend_confirmation(
    symbol: str, bars: Sequence[DailyBar], market_bars: Sequence[DailyBar]
) -> TailCandidate | None:
    """Score a multi-indicator trend only while the market itself is bullish."""
    closes = [bar.close for bar in bars]
    market_closes = [bar.close for bar in market_bars]
    market_ma20 = sma(market_closes, 20)[-1]
    market_ma60 = sma(market_closes, 60)[-1]
    if market_ma20 <= market_ma60:
        return None
    ema20 = ema(closes, 20)[-1]
    ema60 = ema(closes, 60)[-1]
    macd_values = macd(closes)
    dif = macd_values["dif"][-1]
    dea = macd_values["dea"][-1]
    rsi14 = rsi(closes, 14)[-1]
    bollinger_mid = sma(closes, 20)[-1]
    if not (ema20 > ema60 and dif > dea and 45.0 <= rsi14 <= 75.0 and closes[-1] > bollinger_mid):
        return None
    momentum20 = closes[-1] / closes[-21] - 1.0
    score = max(dif - dea, 0.0) * 100.0 + momentum20 * 1_000.0 + (75.0 - rsi14)
    return TailCandidate(
        strategy_id="trend_confirmation",
        symbol=symbol,
        score=score,
        metrics={
            "market_ma20": market_ma20,
            "market_ma60": market_ma60,
            "ema20": ema20,
            "ema60": ema60,
            "dif": dif,
            "dea": dea,
            "rsi14": rsi14,
            "momentum20": momentum20,
        },
        stop_loss_pct=-0.05,
        take_profit_pct=0.10,
        max_holding_days=10,
        exit_rule="macd_turns_weak",
    )


def _score_macd_divergence(
    symbol: str, bars: Sequence[DailyBar], market_bars: Sequence[DailyBar]
) -> TailCandidate | None:
    """Score a confirmed bullish MACD divergence while the market trend is positive."""
    market_closes = [bar.close for bar in market_bars]
    if sma(market_closes, 20)[-1] <= sma(market_closes, 60)[-1]:
        return None
    closes = [bar.close for bar in bars]
    dif_values = macd(closes)["dif"]
    lookback = 14
    if not has_bullish_divergence(closes[-lookback:], dif_values[-lookback:]):
        return None
    latest = bars[-1]
    previous = bars[-2]
    if latest.close <= latest.open or latest.close <= previous.high:
        return None
    split = lookback // 2
    first_index = min(range(split), key=lambda index: closes[-lookback:][index])
    second_index = min(range(split, lookback), key=lambda index: closes[-lookback:][index])
    price_low_delta = closes[-lookback + first_index] / closes[-lookback + second_index] - 1.0
    dif_delta = dif_values[-lookback + second_index] - dif_values[-lookback + first_index]
    amount_ratio = latest.amount / previous.amount if previous.amount > 0 else 0.0
    score = price_low_delta * 100.0 + dif_delta * 100.0 + min(amount_ratio, 3.0)
    return TailCandidate(
        strategy_id="macd_divergence",
        symbol=symbol,
        score=score,
        metrics={
            "price_low_delta": price_low_delta,
            "dif_low_delta": dif_delta,
            "amount_ratio": amount_ratio,
        },
        stop_loss_pct=-0.05,
        take_profit_pct=0.08,
        max_holding_days=10,
        exit_rule="close_below_ma5",
    )


def _score_cup_handle_breakout(symbol: str, bars: Sequence[DailyBar]) -> TailCandidate | None:
    """Score a shrinking-volume handle that breaks out at the tail close."""
    lookback = 14
    pattern = bars[-lookback:]
    closes = [bar.close for bar in pattern]
    amounts = [bar.amount for bar in pattern]
    if not is_cup_handle_breakout(closes, amounts):
        return None
    handle_size = max(3, lookback // 4)
    handle = pattern[-handle_size:]
    cup = pattern[:-handle_size]
    handle_low = min(bar.low for bar in handle)
    handle_high = max(bar.high for bar in handle[:-1])
    cup_average_amount = sum(bar.amount for bar in cup) / len(cup)
    handle_average_amount = sum(bar.amount for bar in handle) / len(handle)
    breakout_pct = closes[-1] / handle_high - 1.0 if handle_high > 0 else 0.0
    score = breakout_pct * 1_000.0 + max(cup_average_amount / handle_average_amount - 1.0, 0.0)
    return TailCandidate(
        strategy_id="cup_handle_breakout",
        symbol=symbol,
        score=score,
        metrics={
            "breakout_pct": breakout_pct,
            "handle_low": handle_low,
            "handle_amount_ratio": handle_average_amount / cup_average_amount,
        },
        stop_loss_pct=-0.06,
        take_profit_pct=0.12,
        max_holding_days=10,
        exit_rule="close_below_handle_low",
    )


def read_symbols_file(path: str | Path) -> list[str]:
    """Read a one-symbol-per-line TDX universe file."""
    source = Path(path)
    if not source.is_file():
        raise ValueError(f"股票池文件不存在: {source}")
    symbols = sorted(
        {
            line.strip().upper()
            for line in source.read_text(encoding="utf-8-sig").splitlines()
            if line.strip() and not line.lstrip().startswith("#")
        }
    )
    if not symbols:
        raise ValueError("股票池文件为空；请每行填写一个通达信证券代码，例如 000001.SZ")
    return symbols


def scan_candidates(
    tq: Any,
    strategy_id: str,
    symbols: Sequence[str],
    market_symbol: str,
    bar_count: int,
    batch_size: int,
) -> tuple[list[TailCandidate], list[str]]:
    """Fetch current daily bars from TDX and return at most three candidates."""
    required_symbols = sorted({*(symbol.upper() for symbol in symbols), market_symbol.upper()})
    bars_by_symbol, errors = fetch_board_bars(tq, required_symbols, bar_count, batch_size)
    market_bars = bars_by_symbol.get(market_symbol.upper())
    if strategy_id in {"trend_confirmation", "macd_divergence"} and not market_bars:
        raise RuntimeError(f"未取得市场指数日线: {market_symbol}")
    candidates = [
        candidate
        for symbol in sorted(set(symbol.upper() for symbol in symbols))
        for candidate in [score_strategy(strategy_id, symbol, bars_by_symbol.get(symbol, ()), market_bars)]
        if candidate is not None
    ]
    return select_top_candidates(candidates), errors


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="A 股尾盘短线四策略扫描器（只生成候选，不执行下单）")
    parser.add_argument("--strategy", choices=[item.strategy_id for item in available_tail_strategies()], required=True)
    parser.add_argument("--symbols-file", required=True, help="股票池文本文件；每行一个通达信证券代码")
    parser.add_argument("--tdx-path", default=r"F:\\new_tdx64", help="通达信安装目录")
    parser.add_argument("--market-symbol", default="000300.SH", help="市场过滤指数代码")
    parser.add_argument("--bar-count", type=int, default=130, help="每只证券读取的未复权日线数量")
    parser.add_argument("--batch-size", type=int, default=200, help="通达信批量读取证券数")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    """Run a live tail scan through the existing Windows TDX client."""
    args = parse_args(argv)
    if args.bar_count < MIN_HISTORY_BARS or args.batch_size <= 0:
        print(f"日线数量至少为 {MIN_HISTORY_BARS}，批次大小必须大于 0", file=sys.stderr)
        return 2
    tq = None
    try:
        symbols = read_symbols_file(args.symbols_file)
        tq = load_tq(args.tdx_path)
        candidates, errors = scan_candidates(
            tq, args.strategy, symbols, args.market_symbol, args.bar_count, args.batch_size
        )
        print(f"策略: {args.strategy}")
        print(f"股票池: {len(symbols)} 只；执行候选: {len(candidates)} 只（上限 {MAX_POSITIONS}）")
        for rank, candidate in enumerate(candidates, start=1):
            print(
                f"{rank}. {candidate.symbol} score={candidate.score:.2f} "
                f"止损={candidate.stop_loss_pct:.1%} 止盈={candidate.take_profit_pct:.1%} "
                f"最长={candidate.max_holding_days}日"
            )
        for error in errors:
            print(f"数据警告: {error}", file=sys.stderr)
        return 0
    except Exception as error:
        print(f"尾盘策略扫描失败: {error}", file=sys.stderr)
        return 1
    finally:
        if tq is not None and hasattr(tq, "close"):
            tq.close()
