"""Strategy registry for independent short-term trading workflows."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Sequence

from modules.short_term.strategies import brick, tail


@dataclass(frozen=True)
class ShortTermStrategy:
    strategy_id: str
    display_name: str
    description: str
    run: Callable[[Sequence[str] | None], int]


def available_strategies() -> list[ShortTermStrategy]:
    """Return registered strategies without importing ledger or market-data code."""
    return [
        ShortTermStrategy(
            strategy_id="brick",
            display_name="砖型图超短",
            description="通达信概念板块联动的砖型图候选筛选与评分。",
            run=brick.main,
        ),
        ShortTermStrategy(
            strategy_id="tail",
            display_name="A股尾盘短线四策略",
            description="尾盘入场、最多三只持仓的四类日线短线候选。",
            run=tail.main,
        ),
    ]


def get_strategy(strategy_id: str) -> ShortTermStrategy:
    normalized = strategy_id.strip().lower()
    for strategy in available_strategies():
        if strategy.strategy_id == normalized:
            return strategy
    raise ValueError(f"未知超短策略: {strategy_id}")
