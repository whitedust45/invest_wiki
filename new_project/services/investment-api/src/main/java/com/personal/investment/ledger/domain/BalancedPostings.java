package com.personal.investment.ledger.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable double-entry postings, balanced independently for every native currency. */
public record BalancedPostings(List<Posting> postings) {
  public BalancedPostings {
    postings = List.copyOf(postings);
    if (postings.size() < 2) {
      throw new IllegalArgumentException("a transaction requires at least two postings");
    }
    ensureBalanced(postings);
  }

  public static BalancedPostings of(List<Posting> postings) {
    return new BalancedPostings(postings);
  }

  private static void ensureBalanced(List<Posting> postings) {
    Map<CurrencyCode, Long> debitTotals = new EnumMap<>(CurrencyCode.class);
    Map<CurrencyCode, Long> creditTotals = new EnumMap<>(CurrencyCode.class);
    for (Posting posting : postings) {
      Map<CurrencyCode, Long> target = posting.side() == PostingSide.DEBIT
          ? debitTotals : creditTotals;
      CurrencyCode currency = posting.amount().currency();
      try {
        target.merge(currency, posting.amount().cent(), Math::addExact);
      } catch (ArithmeticException exception) {
        throw new IllegalArgumentException("posting total overflow", exception);
      }
    }

    for (CurrencyCode currency : CurrencyCode.values()) {
      long debits = debitTotals.getOrDefault(currency, 0L);
      long credits = creditTotals.getOrDefault(currency, 0L);
      if (debits != credits) {
        throw new IllegalArgumentException("postings must balance in " + currency);
      }
    }
  }
}
