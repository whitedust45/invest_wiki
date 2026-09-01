package com.personal.investment.portfolio.application;

/** Per-account valuation visibility. A user-level total is intentionally never apportioned across accounts. */
public enum PortfolioPositionValuationStatus {
  MANUAL_UNIT_PRICE,
  MANUAL_TOTAL_UNALLOCATED,
  UNVALUED,
  EXPIRED,
  PRECISION_UNAVAILABLE,
  FUTURES_SETTLEMENT_ONLY
}
