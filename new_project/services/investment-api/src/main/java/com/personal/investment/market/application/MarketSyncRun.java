package com.personal.investment.market.application;

import java.time.Instant;
import java.time.LocalDate;

public record MarketSyncRun(String marketSyncRunId, LocalDate tradingDate, String runType, String status,
                            Instant startedAt, Instant completedAt) {
}
