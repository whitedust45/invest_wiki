package com.personal.investment.portfolio.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.math.BigDecimal;
import java.util.Objects;

public record PortfolioPositionView(String cashAccountId, String instrumentId, CurrencyCode currency,
                                    BigDecimal quantity, Long marketValueCent,
                                    Long costCent, Long unrealizedPnlCent,
                                    PortfolioPositionValuationStatus valuationStatus) {
  public PortfolioPositionView {
    Objects.requireNonNull(currency, "currency must not be null");
    Objects.requireNonNull(quantity, "quantity must not be null");
    Objects.requireNonNull(valuationStatus, "valuationStatus must not be null");
  }

  public PortfolioPositionView(String cashAccountId, String instrumentId, CurrencyCode currency,
                               BigDecimal quantity, Long marketValueCent,
                               PortfolioPositionValuationStatus valuationStatus) {
    this(cashAccountId, instrumentId, currency, quantity, marketValueCent, null, null, valuationStatus);
  }
}
