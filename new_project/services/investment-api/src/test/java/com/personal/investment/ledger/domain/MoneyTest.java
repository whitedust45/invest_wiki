package com.personal.investment.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class MoneyTest {
  @Test
  void storesOriginalCurrencyMinorUnitsAsAWholeNumber() {
    Money amount = Money.of(666, CurrencyCode.USD);

    assertThat(amount.cent()).isEqualTo(666);
    assertThat(amount.currency()).isEqualTo(CurrencyCode.USD);
  }

  @Test
  void rejectsNegativeAmountsAndUnsupportedCurrencies() {
    assertThatIllegalArgumentException().isThrownBy(() -> Money.of(-1, CurrencyCode.CNY));
    assertThatIllegalArgumentException().isThrownBy(() -> CurrencyCode.of("JPY"));
    assertThatIllegalArgumentException().isThrownBy(() -> CurrencyCode.of("usd"));
  }

  @Test
  void onlyAddsAmountsInTheSameCurrency() {
    assertThat(Money.of(600, CurrencyCode.USD).plus(Money.of(66, CurrencyCode.USD)).cent())
        .isEqualTo(666);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> Money.of(1, CurrencyCode.USD).plus(Money.of(1, CurrencyCode.CNY)));
  }

  @Test
  void subtractsOnlyInTheSameCurrencyWithoutAllowingNegativeBalances() {
    assertThat(Money.of(666, CurrencyCode.USD).minus(Money.of(6, CurrencyCode.USD)).cent())
        .isEqualTo(660);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> Money.of(1, CurrencyCode.CNY).minus(Money.of(2, CurrencyCode.CNY)));
  }
}
