package com.personal.investment.platform.application;

import java.time.LocalDate;

public record LegacyDividendEntitlementOverride(int sourceRow, LocalDate entitlementDate) {
  public LegacyDividendEntitlementOverride {
    if (sourceRow < 1 || entitlementDate == null) {
      throw new IllegalArgumentException("legacy dividend entitlement override is invalid");
    }
  }
}
