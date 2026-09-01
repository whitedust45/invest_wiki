package com.personal.investment.ledger.application;

import java.util.Objects;

/** A command only: successful execution appends a close fact and an open fact in one transaction. */
public record FuturesRollCommand(FuturesCloseCommand closeLeg, FuturesOpenCommand openLeg) {
  public FuturesRollCommand {
    Objects.requireNonNull(closeLeg, "closeLeg must not be null");
    Objects.requireNonNull(openLeg, "openLeg must not be null");
    if (!closeLeg.occurredOn().equals(openLeg.occurredOn())) {
      throw new IllegalArgumentException("futures roll legs must use the same occurredOn date");
    }
    if (closeLeg.instrumentId().equals(openLeg.instrumentId())) {
      throw new IllegalArgumentException("futures roll must move to a different contract");
    }
  }
}
