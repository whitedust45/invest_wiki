package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V5ManualValuationSchemaSpecificationTest {
  private static final Path V5 = Path.of(
      "src/main/resources/db/migration/V5__manual_valuation_exclusivity.sql");

  @Test
  void v5MakesManualValuationAmountsPositiveAndMutuallyExclusiveWithoutForeignKeys() throws Exception {
    assertThat(V5).exists();
    String sql = Files.readString(V5);

    assertThat(sql)
        .contains("ck_manual_valuation_exactly_one_amount")
        .contains("ck_manual_valuation_positive_amount")
        .contains("market_value_cent IS NOT NULL AND unit_price_cent IS NULL")
        .contains("market_value_cent IS NULL AND unit_price_cent IS NOT NULL");
    assertThat(sql.toUpperCase()).doesNotContain("FOREIGN KEY");
  }
}
