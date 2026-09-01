package com.personal.investment.portfolio.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.util.Objects;

/** Read-only native-currency cash and margin balance for one user cash account at a business date. */
public record PortfolioAccountBalance(String cashAccountId, CurrencyCode currency, long cashCent, long marginCent) {
  public PortfolioAccountBalance {
    if (cashAccountId == null || cashAccountId.isBlank()) {
      throw new IllegalArgumentException("cashAccountId must not be blank");
    }
    Objects.requireNonNull(currency, "currency must not be null");
  }
}
