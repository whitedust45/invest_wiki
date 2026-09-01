#!/usr/bin/env python3
"""Daily-bar portfolio backtester for the A-share tail strategy suite."""

from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from dataclasses import asdict
from datetime import datetime, timedelta
import json
from pathlib import Path
import sys
from typing import Any, Sequence

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from modules.short_term.backtest_brick import fetch_historical_bars
from modules.short_term.backtest_cache import BacktestBarCache
from modules.short_term.strategies.brick import DailyBar
from modules.short_term.strategies.tail import (
    available_tail_strategies,
    load_tq,
    macd,
    read_symbols_file,
    score_strategy,
    select_top_candidates,
    sma,
)


@dataclass(frozen=True)
class BacktestConfig:
    """Configurable A-share transaction friction and portfolio limits."""

    initial_cash: float = 100_000.0
    max_positions: int = 3
    commission_rate: float = 0.0001
    sell_stamp_tax_rate: float = 0.0005
    buy_slippage: float = 0.0005
    sell_slippage: float = 0.0005


@dataclass(frozen=True)
class Position:
    """A fully filled tail entry that remains unavailable for same-day sale."""

    symbol: str
    strategy_id: str
    entry_date: str
    entry_price: float
    quantity: int
    stop_loss_pct: float
    take_profit_pct: float
    max_holding_days: int
    exit_rule: str


@dataclass(frozen=True)
class ExitDecision:
    """One deterministic daily-bar exit decision before fees."""

    reason: str
    exit_price: float


@dataclass(frozen=True)
class Trade:
    """One closed round trip, retaining return, cash P&L, and all friction."""

    strategy_id: str
    symbol: str
    entry_date: str
    exit_date: str
    entry_price: float
    exit_price: float
    quantity: int
    reason: str
    gross_return: float
    net_pnl: float
    fees: float


@dataclass(frozen=True)
class BacktestResult:
    """Pure replay output, intentionally independent of any TDX connection."""

    trades: list[Trade]
    equity_curve: list[float]
    max_open_positions: int


@dataclass(frozen=True)
class _ActivePosition:
    position: Position
    entry_index: int
    entry_cost: float


def kelly_fraction(win_rate: float, payoff_ratio: float) -> float:
    """Return full Kelly fraction, clamped only at zero for non-positive edges."""
    if not 0.0 <= win_rate <= 1.0:
        raise ValueError("胜率必须在 0 到 1 之间")
    if payoff_ratio <= 0:
        raise ValueError("盈亏比必须大于 0")
    return max(0.0, win_rate - (1.0 - win_rate) / payoff_ratio)


def execute_exit(position: Position, bar: DailyBar, config: BacktestConfig) -> ExitDecision:
    """Apply gap, stop-first intraday, and take-profit rules to one eligible day."""
    stop_price = position.entry_price * (1.0 + position.stop_loss_pct)
    take_price = position.entry_price * (1.0 + position.take_profit_pct)
    if bar.open <= stop_price:
        return ExitDecision("gap_stop_loss", bar.open * (1.0 - config.sell_slippage))
    if bar.open >= take_price:
        return ExitDecision("gap_take_profit", bar.open * (1.0 - config.sell_slippage))
    if bar.low <= stop_price:
        return ExitDecision("stop_loss", stop_price * (1.0 - config.sell_slippage))
    if bar.high >= take_price:
        return ExitDecision("take_profit", take_price * (1.0 - config.sell_slippage))
    return ExitDecision("hold", 0.0)


def _maximum_drawdown(equity_curve: Sequence[float]) -> float:
    peak = 0.0
    drawdown = 0.0
    for value in equity_curve:
        peak = max(peak, value)
        if peak > 0:
            drawdown = min(drawdown, value / peak - 1.0)
    return drawdown


def build_report_payload(
    strategy_id: str,
    start_date: str,
    end_date: str,
    trades: Sequence[Trade],
    equity_curve: Sequence[float],
) -> dict[str, Any]:
    """Build stable, JSON-safe portfolio statistics from completed transactions."""
    winning = [trade.gross_return for trade in trades if trade.gross_return > 0]
    losing = [trade.gross_return for trade in trades if trade.gross_return < 0]
    win_rate = len(winning) / len(trades) if trades else 0.0
    average_win = sum(winning) / len(winning) if winning else 0.0
    average_loss = abs(sum(losing) / len(losing)) if losing else 0.0
    payoff_ratio = average_win / average_loss if average_loss > 0 else 0.0
    return {
        "strategy_id": strategy_id,
        "start_date": start_date,
        "end_date": end_date,
        "max_positions": 3,
        "trade_count": len(trades),
        "win_rate": win_rate,
        "average_win": average_win,
        "average_loss": average_loss,
        "payoff_ratio": payoff_ratio,
        "kelly_fraction": kelly_fraction(win_rate, payoff_ratio) if payoff_ratio > 0 else 0.0,
        "net_pnl": sum(trade.net_pnl for trade in trades),
        "total_fees": sum(trade.fees for trade in trades),
        "maximum_drawdown": _maximum_drawdown(equity_curve),
        "source_performance_is_not_reproduction": True,
        "limitations": [
            "来源页面的历史统计不等于本模块改造后策略的回测结果。",
            "信号日收盘价仅近似尾盘成交价。",
            "日线先后顺序无法重建同日止盈与止损触发先后，回测按止损优先。",
            "当前股票池和日线不能完整重建历史 ST、停牌、退市、成分变化与涨跌停制度变化。",
            "改造策略待 Windows + 通达信环境复测；本 Mac 未运行真实回测。",
        ],
    }


def render_report_markdown(payload: dict[str, Any]) -> str:
    """Render a concise human-readable disclosure-first backtest report."""
    lines = [
        "# A股尾盘短线策略回测",
        "",
        f"- 策略：{payload['strategy_id']}",
        f"- 区间：{payload['start_date']} 至 {payload['end_date']}",
        f"- 实际持仓上限：{payload['max_positions']}",
        f"- 交易次数：{payload['trade_count']}",
        f"- 胜率：{payload['win_rate']:.2%}",
        f"- 平均盈利：{payload['average_win']:.2%}",
        f"- 平均亏损：{payload['average_loss']:.2%}",
        f"- 盈亏比：{payload['payoff_ratio']:.4f}",
        f"- 凯利值：{payload['kelly_fraction']:.2%}",
        f"- 净盈亏：{payload['net_pnl']:.2f}",
        f"- 总费用：{payload['total_fees']:.2f}",
        f"- 最大回撤：{payload['maximum_drawdown']:.2%}",
        "",
        "## 数据与执行限制",
        "",
        *[f"- {item}" for item in payload["limitations"]],
        "",
    ]
    return "\n".join(lines)


def write_backtest_outputs(
    output_dir: str | Path, payload: dict[str, Any], trades: Sequence[Trade]
) -> Path:
    """Persist CSV, JSON, and Markdown artifacts for one strategy replay."""
    destination = Path(output_dir)
    destination.mkdir(parents=True, exist_ok=True)
    with (destination / "trades.csv").open("w", encoding="utf-8", newline="") as stream:
        fieldnames = list(Trade.__dataclass_fields__)
        writer = csv.DictWriter(stream, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(asdict(trade) for trade in trades)
    (destination / "report.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (destination / "report.md").write_text(render_report_markdown(payload), encoding="utf-8")
    return destination


def _technical_exit(position: Position, bars: Sequence[DailyBar]) -> bool:
    closes = [bar.close for bar in bars]
    if position.exit_rule == "close_below_ma5":
        return len(closes) >= 5 and closes[-1] < sma(closes, 5)[-1]
    if position.exit_rule == "macd_turns_weak":
        values = macd(closes)
        return values["dif"][-1] <= values["dea"][-1]
    return False


def _close_active_position(
    active: _ActivePosition,
    exit_date: str,
    exit_price: float,
    reason: str,
    cash: float,
    config: BacktestConfig,
) -> tuple[Trade, float]:
    gross_proceeds = exit_price * active.position.quantity
    sell_fees = gross_proceeds * (config.commission_rate + config.sell_stamp_tax_rate)
    net_proceeds = gross_proceeds - sell_fees
    gross_return = exit_price / active.position.entry_price - 1.0
    return (
        Trade(
            strategy_id=active.position.strategy_id,
            symbol=active.position.symbol,
            entry_date=active.position.entry_date,
            exit_date=exit_date,
            entry_price=active.position.entry_price,
            exit_price=exit_price,
            quantity=active.position.quantity,
            reason=reason,
            gross_return=gross_return,
            net_pnl=net_proceeds - active.entry_cost,
            fees=active.entry_cost - active.position.entry_price * active.position.quantity + sell_fees,
        ),
        cash + net_proceeds,
    )


def run_backtest_from_bars(
    strategy_id: str,
    bars_by_symbol: dict[str, Sequence[DailyBar]],
    market_bars: Sequence[DailyBar] | None,
    config: BacktestConfig,
) -> BacktestResult:
    """Replay tail signals over supplied daily bars with an equal-weight three-stock cap."""
    if config.max_positions <= 0 or config.max_positions > 3:
        raise ValueError("尾盘短线回测持仓上限必须在 1 到 3 之间")
    by_symbol_date = {
        symbol: {bar.date: bar for bar in bars} for symbol, bars in bars_by_symbol.items()
    }
    all_dates = sorted({bar.date for bars in bars_by_symbol.values() for bar in bars})
    market_by_date = {bar.date: bar for bar in market_bars or ()}
    cash = config.initial_cash
    active: list[_ActivePosition] = []
    trades: list[Trade] = []
    equity_curve: list[float] = [cash]
    max_open_positions = 0
    for date in all_dates:
        still_open: list[_ActivePosition] = []
        for item in active:
            current_bar = by_symbol_date.get(item.position.symbol, {}).get(date)
            if current_bar is None or date <= item.position.entry_date:
                still_open.append(item)
                continue
            history = [bar for bar in bars_by_symbol[item.position.symbol] if bar.date <= date]
            decision = execute_exit(item.position, current_bar, config)
            if decision.reason == "hold" and _technical_exit(item.position, history):
                decision = ExitDecision("technical_exit", current_bar.close * (1.0 - config.sell_slippage))
            if decision.reason == "hold" and len(history) - item.entry_index >= item.position.max_holding_days:
                decision = ExitDecision("max_holding_days", current_bar.close * (1.0 - config.sell_slippage))
            if decision.reason == "hold":
                still_open.append(item)
            else:
                trade, cash = _close_active_position(item, date, decision.exit_price, decision.reason, cash, config)
                trades.append(trade)
        active = still_open
        market_history = [bar for bar in market_bars or () if bar.date <= date]
        candidates = []
        held_symbols = {item.position.symbol for item in active}
        for symbol, bars in bars_by_symbol.items():
            if symbol in held_symbols:
                continue
            history = [bar for bar in bars if bar.date <= date]
            candidate = score_strategy(strategy_id, symbol, history, market_history)
            if candidate is not None:
                candidates.append(candidate)
        for candidate in select_top_candidates(candidates, config.max_positions - len(active)):
            current_bar = by_symbol_date[candidate.symbol].get(date)
            if current_bar is None:
                continue
            slots = config.max_positions - len(active)
            if slots <= 0:
                break
            entry_price = current_bar.close * (1.0 + config.buy_slippage)
            allocation = cash / slots
            quantity = int(allocation / (entry_price * (1.0 + config.commission_rate)) / 100.0) * 100
            if quantity <= 0:
                continue
            entry_cost = entry_price * quantity * (1.0 + config.commission_rate)
            cash -= entry_cost
            history = [bar for bar in bars_by_symbol[candidate.symbol] if bar.date <= date]
            active.append(
                _ActivePosition(
                    Position(
                        symbol=candidate.symbol,
                        strategy_id=candidate.strategy_id,
                        entry_date=date,
                        entry_price=entry_price,
                        quantity=quantity,
                        stop_loss_pct=candidate.stop_loss_pct,
                        take_profit_pct=candidate.take_profit_pct,
                        max_holding_days=candidate.max_holding_days,
                        exit_rule=candidate.exit_rule,
                    ),
                    len(history),
                    entry_cost,
                )
            )
        max_open_positions = max(max_open_positions, len(active))
        marked_value = cash + sum(
            by_symbol_date[item.position.symbol].get(date, DailyBar(date, 0, 0, 0, 0)).close * item.position.quantity
            for item in active
        )
        equity_curve.append(marked_value)
    return BacktestResult(trades=trades, equity_curve=equity_curve, max_open_positions=max_open_positions)


def _parse_iso_date(value: str) -> datetime:
    try:
        return datetime.strptime(value, "%Y-%m-%d")
    except ValueError as error:
        raise ValueError("日期必须使用 YYYY-MM-DD 格式") from error


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="A 股尾盘短线四策略日线回测（Windows + 通达信）")
    parser.add_argument("--strategy", choices=[item.strategy_id for item in available_tail_strategies()], required=True)
    parser.add_argument("--symbols-file", required=True, help="股票池文本文件；每行一个通达信证券代码")
    parser.add_argument("--start-date", required=True, help="回测开始日期 YYYY-MM-DD")
    parser.add_argument("--end-date", required=True, help="回测结束日期 YYYY-MM-DD")
    parser.add_argument("--tdx-path", default=r"F:\\new_tdx64", help="通达信安装目录")
    parser.add_argument("--market-symbol", default="000300.SH", help="市场过滤指数代码")
    parser.add_argument("--initial-cash", type=float, default=100_000.0, help="初始资金")
    parser.add_argument("--commission-rate", type=float, default=0.0001, help="买卖佣金比例")
    parser.add_argument("--sell-stamp-tax-rate", type=float, default=0.0005, help="卖出印花税比例")
    parser.add_argument("--buy-slippage", type=float, default=0.0005, help="买入滑点比例")
    parser.add_argument("--sell-slippage", type=float, default=0.0005, help="卖出滑点比例")
    parser.add_argument("--batch-size", type=int, default=200, help="通达信批量读取证券数")
    parser.add_argument("--cache-path", default="data/short-term/tail-backtests/backtest-cache.db", help="SQLite 日线缓存路径")
    parser.add_argument("--output-root", default="data/short-term/tail-backtests", help="报告根目录")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    """Fetch TDX daily bars, replay one strategy, and persist three artifacts."""
    args = parse_args(argv)
    tq = None
    cache = None
    try:
        start = _parse_iso_date(args.start_date)
        end = _parse_iso_date(args.end_date)
        if start > end:
            raise ValueError("回测结束日期不得早于开始日期")
        if args.initial_cash <= 0 or args.batch_size <= 0:
            raise ValueError("初始资金和批次大小必须大于 0")
        symbols = read_symbols_file(args.symbols_file)
        all_symbols = sorted({*symbols, args.market_symbol.upper()})
        warmup_start = (start - timedelta(days=180)).strftime("%Y-%m-%d")
        tq = load_tq(args.tdx_path)
        cache = BacktestBarCache(args.cache_path)
        bars_by_symbol, errors, _ = fetch_historical_bars(
            tq,
            all_symbols,
            warmup_start,
            args.end_date,
            args.batch_size,
            cache=cache,
        )
        market_bars = bars_by_symbol.pop(args.market_symbol.upper(), None)
        if not market_bars:
            raise RuntimeError(f"未取得市场指数日线: {args.market_symbol}")
        config = BacktestConfig(
            initial_cash=args.initial_cash,
            commission_rate=args.commission_rate,
            sell_stamp_tax_rate=args.sell_stamp_tax_rate,
            buy_slippage=args.buy_slippage,
            sell_slippage=args.sell_slippage,
        )
        result = run_backtest_from_bars(args.strategy, bars_by_symbol, market_bars, config)
        payload = build_report_payload(args.strategy, args.start_date, args.end_date, result.trades, result.equity_curve)
        payload["data_errors"] = errors
        payload["max_open_positions"] = result.max_open_positions
        output_dir = Path(args.output_root) / f"{args.start_date.replace('-', '')}_{args.end_date.replace('-', '')}" / args.strategy
        write_backtest_outputs(output_dir, payload, result.trades)
        print(f"回测完成：{output_dir}")
        print(f"交易次数：{payload['trade_count']}；胜率：{payload['win_rate']:.2%}；凯利：{payload['kelly_fraction']:.2%}")
        return 0
    except Exception as error:
        print(f"尾盘策略回测失败: {error}", file=sys.stderr)
        return 1
    finally:
        if cache is not None:
            cache.close()
        if tq is not None and hasattr(tq, "close"):
            tq.close()


if __name__ == "__main__":
    raise SystemExit(main())
