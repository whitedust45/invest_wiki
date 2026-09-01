package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerTransaction;
import java.util.Objects;

/** Immutable target facts loaded solely to create an append-only reversal. */
public record CorrectionTarget(LedgerTransaction transaction, CorporateActionDetail corporateAction,
                               String strategyKey) {
  public CorrectionTarget {
    Objects.requireNonNull(transaction, "transaction must not be null");
  }
}
