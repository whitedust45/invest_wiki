package com.personal.investment.strategy.application;

/** Owner-isolated read model for one strategy detail page; every nested value remains an immutable fact/version. */
public record StrategyWorkspaceSnapshot(StrategyCard card, StrategyRuleVersion activeRule,
                                        StrategyEvaluation latestEvaluation) {
}
