package com.personal.investment.portfolio.application;

import java.util.List;

public record PortfolioReconciliationPage(List<PortfolioReconciliationView> items, String nextCursor) {
  public PortfolioReconciliationPage {
    items = List.copyOf(items);
  }
}
