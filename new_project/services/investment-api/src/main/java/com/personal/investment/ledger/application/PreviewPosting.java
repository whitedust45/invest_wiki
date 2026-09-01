package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.PostingSide;
import java.util.Objects;

public record PreviewPosting(String accountCode, String displayName, PostingSide postingSide, long amountCent,
                             CurrencyCode currency) {
  public PreviewPosting {
    if (accountCode == null || accountCode.isBlank() || displayName == null || displayName.isBlank()
        || amountCent <= 0) {
      throw new IllegalArgumentException("preview posting is invalid");
    }
    Objects.requireNonNull(postingSide, "postingSide must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
  }
}
