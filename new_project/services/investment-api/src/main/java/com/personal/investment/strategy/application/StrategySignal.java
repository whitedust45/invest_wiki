package com.personal.investment.strategy.application;

import java.time.Instant;

public record StrategySignal(String strategySignalId, String strategyEvaluationId, String signalRunId,
                             String instrumentId, StrategySignalScope signalScope, String signalKey,
                             StrategyEvaluationStatus signalType, String severity, short rankNo,
                             String explanation, Instant asOfAt, Instant createdAt) {
}
