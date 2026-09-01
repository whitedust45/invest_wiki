package com.personal.investment.portfolio.application;

import com.personal.investment.portfolio.domain.ManualValuation;

/** Append-only storage boundary for manual valuation facts. */
@FunctionalInterface
public interface ManualValuationPort {
  void append(ManualValuation valuation);
}
