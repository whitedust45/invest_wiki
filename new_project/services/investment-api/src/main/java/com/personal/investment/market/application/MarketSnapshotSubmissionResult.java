package com.personal.investment.market.application;

public record MarketSnapshotSubmissionResult(String marketSnapshotSubmissionId, String marketSyncRunId,
                                             String status) {
}
