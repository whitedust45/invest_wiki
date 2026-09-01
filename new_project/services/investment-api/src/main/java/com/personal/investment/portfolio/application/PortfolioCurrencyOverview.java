package com.personal.investment.portfolio.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record PortfolioCurrencyOverview(CurrencyCode currency, long cashCent, long marginCent, Long marketValueCent,
                                        Long netAssetCent, List<PortfolioPositionView> positions, LocalDate asOf,
                                        long sourceLedgerVersion, PortfolioValuationStatus valuationStatus) {
  public PortfolioCurrencyOverview {
    Objects.requireNonNull(currency, "currency must not be null");
    positions = List.copyOf(positions);
    Objects.requireNonNull(asOf, "asOf must not be null");
    if (sourceLedgerVersion < 0) {
      throw new IllegalArgumentException("sourceLedgerVersion must not be negative");
    }
    Objects.requireNonNull(valuationStatus, "valuationStatus must not be null");
  }
}
