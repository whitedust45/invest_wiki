package com.personal.investment.portfolio.application;

/** Aggregate native-currency valuation completeness; no cross-currency amount is ever produced. */
public enum PortfolioValuationStatus {
  NO_OPEN_POSITION,
  MANUAL,
  MANUAL_TOTAL_UNALLOCATED,
  PARTIALLY_UNVALUED,
  UNVALUED
}
