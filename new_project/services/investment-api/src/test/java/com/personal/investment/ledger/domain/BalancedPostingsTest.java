package com.personal.investment.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class BalancedPostingsTest {
  @Test
  void acceptsACompleteBalancedDoubleEntryInOneCurrency() {
    BalancedPostings postings = BalancedPostings.of(List.of(
        new Posting("asset-investment", PostingSide.DEBIT, Money.of(40_005, CurrencyCode.CNY)),
        new Posting("asset-cash", PostingSide.CREDIT, Money.of(40_005, CurrencyCode.CNY))));

    assertThat(postings.postings()).hasSize(2);
  }

  @Test
  void requiresEachCurrencyToBalanceIndependently() {
    assertThatIllegalArgumentException().isThrownBy(() -> BalancedPostings.of(List.of(
        new Posting("asset-investment", PostingSide.DEBIT, Money.of(100, CurrencyCode.USD)),
        new Posting("asset-cash", PostingSide.CREDIT, Money.of(99, CurrencyCode.USD)))));

    assertThatIllegalArgumentException().isThrownBy(() -> BalancedPostings.of(List.of(
        new Posting("asset-investment", PostingSide.DEBIT, Money.of(100, CurrencyCode.USD)),
        new Posting("asset-cash", PostingSide.CREDIT, Money.of(100, CurrencyCode.CNY)))));
  }

  @Test
  void rejectsZeroAmountOrBlankAccountPostings() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Posting(
        "asset-cash", PostingSide.DEBIT, Money.of(0, CurrencyCode.CNY)));
    assertThatIllegalArgumentException().isThrownBy(() -> new Posting(
        " ", PostingSide.DEBIT, Money.of(1, CurrencyCode.CNY)));
  }
}
