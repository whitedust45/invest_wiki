package com.personal.investment.reporting.application;

import java.time.LocalDate;
import java.util.Objects;

/** Rendering-only coordinate calculated on the server so mini-program clients never perform money arithmetic. */
public record PortfolioHistoryChartPoint(String dailySnapshotId, LocalDate asOfDate, int netAssetBasisPoints) {
  public PortfolioHistoryChartPoint {
    if (dailySnapshotId == null || !dailySnapshotId.matches("^[0-9A-HJKMNP-TV-Z]{26}$")) {
      throw new IllegalArgumentException("dailySnapshotId must be a ULID");
    }
    Objects.requireNonNull(asOfDate, "asOfDate must not be null");
    if (netAssetBasisPoints < 0 || netAssetBasisPoints > 10_000) {
      throw new IllegalArgumentException("netAssetBasisPoints must be within [0, 10000]");
    }
  }
}
