package com.personal.investment.ledger.domain;

import java.util.Objects;

/**
 * Monetary value expressed only in the original currency's two-decimal minor unit.
 * Direction is represented by a posting side, never a signed amount.
 */
public record Money(long cent, CurrencyCode currency) {
  public Money {
    if (cent < 0) {
      throw new IllegalArgumentException("money cent must not be negative");
    }
    Objects.requireNonNull(currency, "currency must not be null");
  }

  public static Money of(long cent, CurrencyCode currency) {
    return new Money(cent, currency);
  }

  public Money plus(Money other) {
    Objects.requireNonNull(other, "other money must not be null");
    if (currency != other.currency) {
      throw new IllegalArgumentException("money currencies must match");
    }
    try {
      return new Money(Math.addExact(cent, other.cent), currency);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("money cent overflow", exception);
    }
  }

  public Money minus(Money other) {
    Objects.requireNonNull(other, "other money must not be null");
    if (currency != other.currency) {
      throw new IllegalArgumentException("money currencies must match");
    }
    if (cent < other.cent) {
      throw new IllegalArgumentException("money subtraction must not produce a negative amount");
    }
    return new Money(cent - other.cent, currency);
  }

  public boolean isPositive() {
    return cent > 0;
  }
}
