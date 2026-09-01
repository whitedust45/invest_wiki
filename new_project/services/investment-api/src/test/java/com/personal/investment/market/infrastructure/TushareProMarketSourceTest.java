package com.personal.investment.market.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TushareProMarketSourceTest {
  @Test
  void convertsOnlyExactlyRepresentablePricesToOriginalCurrencyMinorUnits() {
    assertThat(TushareProMarketSource.toMinorUnitExact("6.66")).isEqualTo(666L);
    assertThatThrownBy(() -> TushareProMarketSource.toMinorUnitExact("6.666"))
        .hasMessageContaining("cents");
  }
}
