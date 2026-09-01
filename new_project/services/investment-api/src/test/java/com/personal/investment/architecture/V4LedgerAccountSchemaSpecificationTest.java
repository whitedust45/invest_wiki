package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V4LedgerAccountSchemaSpecificationTest {
  private static final Path V4 = Path.of(
      "src/main/resources/db/migration/V4__cash_account_name_uniqueness.sql");

  @Test
  void v4EnforcesCashAccountNameUniquenessWithoutForeignKeys() throws Exception {
    assertThat(V4).exists();
    String sql = Files.readString(V4);

    assertThat(sql)
        .contains("cash_display_name")
        .contains("uk_ledger_account_owner_currency_cash_display_name")
        .contains("account_kind = 'ASSET_CASH'");
    assertThat(sql.toUpperCase()).doesNotContain("FOREIGN KEY");
  }
}
