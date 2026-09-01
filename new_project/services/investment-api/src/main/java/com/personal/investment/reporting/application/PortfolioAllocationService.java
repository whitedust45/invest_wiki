package com.personal.investment.reporting.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.portfolio.application.PortfolioCurrencyOverview;
import com.personal.investment.portfolio.application.PortfolioOverviewService;
import com.personal.investment.portfolio.application.PortfolioPositionView;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioAllocationService {
  private final PortfolioOverviewService portfolioOverviewService;

  public PortfolioAllocationService(PortfolioOverviewService portfolioOverviewService) {
    this.portfolioOverviewService = portfolioOverviewService;
  }

  @Transactional(readOnly = true)
  public List<PortfolioAllocation> allocation(String ownerUserId, LocalDate asOfDate) {
    if (ownerUserId == null || !ownerUserId.matches("^[0-9A-HJKMNP-TV-Z]{26}$") || asOfDate == null) {
      throw new IllegalArgumentException("portfolio allocation query is invalid");
    }
    return portfolioOverviewService.summary(ownerUserId, asOfDate).items().stream()
        .map(this::allocation).toList();
  }

  private PortfolioAllocation allocation(PortfolioCurrencyOverview overview) {
    if (overview.marketValueCent() == null) {
      return new PortfolioAllocation(overview.currency(), overview.valuationStatus(), null, List.of());
    }
    Map<String, Long> byInstrument = new LinkedHashMap<>();
    for (PortfolioPositionView position : overview.positions()) {
      if (position.marketValueCent() == null) {
        return new PortfolioAllocation(overview.currency(), overview.valuationStatus(), overview.marketValueCent(), List.of());
      }
      byInstrument.merge(position.instrumentId(), position.marketValueCent(), Math::addExact);
    }
    long total = byInstrument.values().stream().reduce(0L, Math::addExact);
    if (total == 0L) {
      return new PortfolioAllocation(overview.currency(), overview.valuationStatus(), overview.marketValueCent(), List.of());
    }
    List<Map.Entry<String, Long>> entries = new ArrayList<>(byInstrument.entrySet());
    entries.sort(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry::getKey));
    return new PortfolioAllocation(overview.currency(), overview.valuationStatus(), overview.marketValueCent(),
        entries.stream().map(entry -> new PortfolioAllocation.Slice(entry.getKey(), entry.getValue(),
            shareBasisPoints(entry.getValue(), total))).toList());
  }

  private static int shareBasisPoints(long amountCent, long totalCent) {
    return BigInteger.valueOf(amountCent).multiply(BigInteger.valueOf(10_000L)).divide(BigInteger.valueOf(totalCent))
        .intValueExact();
  }
}
