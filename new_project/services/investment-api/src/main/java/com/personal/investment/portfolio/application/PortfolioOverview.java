package com.personal.investment.portfolio.application;

import java.util.List;

public record PortfolioOverview(List<PortfolioCurrencyOverview> items) {
  public PortfolioOverview {
    items = List.copyOf(items);
  }
}
