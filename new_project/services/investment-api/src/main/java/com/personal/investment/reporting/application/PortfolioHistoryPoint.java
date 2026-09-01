package com.personal.investment.reporting.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.portfolio.application.PortfolioValuationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable, native-currency portfolio point used by the report curve. */
public record PortfolioHistoryPoint(String dailySnapshotId, CurrencyCode currency, LocalDate asOfDate,
                                    long netAssetCent, long cashCent, long marketValueCent,
                                    long sourceLedgerVersion, Instant calculatedAt) {
  public PortfolioHistoryPoint {
    requireUlid(dailySnapshotId, "dailySnapshotId");
    Objects.requireNonNull(currency, "currency must not be null");
    Objects.requireNonNull(asOfDate, "asOfDate must not be null");
    if (netAssetCent < 0 || cashCent < 0 || marketValueCent < 0 || sourceLedgerVersion < 0) {
      throw new IllegalArgumentException("portfolio history values must not be negative");
    }
    Objects.requireNonNull(calculatedAt, "calculatedAt must not be null");
  }

  public static PortfolioHistoryPoint fromValuedOverview(String dailySnapshotId, CurrencyCode currency,
      LocalDate asOfDate, Long netAssetCent, long cashCent, Long marketValueCent, long sourceLedgerVersion,
      Instant calculatedAt, PortfolioValuationStatus valuationStatus) {
    if ((valuationStatus != PortfolioValuationStatus.MANUAL
        && valuationStatus != PortfolioValuationStatus.NO_OPEN_POSITION)
        || netAssetCent == null || marketValueCent == null) {
      throw new IllegalArgumentException("only fully valued native-currency portfolios can create a history point");
    }
    return new PortfolioHistoryPoint(dailySnapshotId, currency, asOfDate, netAssetCent, cashCent, marketValueCent,
        sourceLedgerVersion, calculatedAt);
  }

  private static void requireUlid(String value, String field) {
    if (value == null || !value.matches("^[0-9A-HJKMNP-TV-Z]{26}$")) {
      throw new IllegalArgumentException(field + " must be a ULID");
    }
  }
}
