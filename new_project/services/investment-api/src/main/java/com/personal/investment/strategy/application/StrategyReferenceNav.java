package com.personal.investment.strategy.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;

/** Append-only user-maintained USD denominator for the USD strategy workspaces. */
public record StrategyReferenceNav(String strategyReferenceNavId, String ownerUserId, StrategyKey strategyKey,
                                   CurrencyCode currency, long referenceNavCent, Instant asOfAt,
                                   Instant validUntil, String source, Instant createdAt) {
}
