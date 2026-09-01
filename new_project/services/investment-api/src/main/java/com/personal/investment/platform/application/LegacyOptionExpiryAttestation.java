package com.personal.investment.platform.application;

import com.personal.investment.ledger.application.OptionExpiryOutcome;

public record LegacyOptionExpiryAttestation(int sourceRow, OptionExpiryOutcome expiryOutcome) {
  public LegacyOptionExpiryAttestation {
    if (sourceRow < 1 || expiryOutcome != OptionExpiryOutcome.WORTHLESS) {
      throw new IllegalArgumentException("legacy option expiry must be explicitly attested as WORTHLESS");
    }
  }
}
