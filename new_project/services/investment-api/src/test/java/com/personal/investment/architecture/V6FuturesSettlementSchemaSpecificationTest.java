package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V6FuturesSettlementSchemaSpecificationTest {
  private static final Path V6 = Path.of(
      "src/main/resources/db/migration/V6__futures_lot_settlement_date.sql");

  @Test
  void v6BackfillsAndMakesTheFuturesSettlementBaselineDateMandatoryWithoutForeignKeys() throws Exception {
    assertThat(V6).exists();
    String sql = Files.readString(V6);

    assertThat(sql)
        .contains("ADD COLUMN last_settlement_on DATE NULL")
        .contains("SET last_settlement_on = opened_on")
        .contains("MODIFY COLUMN last_settlement_on DATE NOT NULL")
        .contains("idx_futures_position_lot_settlement");
    assertThat(sql.toUpperCase()).doesNotContain("FOREIGN KEY");
  }
}
