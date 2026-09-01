package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V3LedgerSchemaSpecificationTest {
  private static final Path V3 = Path.of(
      "src/main/resources/db/migration/V3__ledger_portfolio_import_foundation.sql");

  @Test
  void v3ProtectsTheApprovedLedgerAndFileLifecycleInvariants() throws Exception {
    assertThat(V3).exists();
    String sql = Files.readString(V3);

    assertThat(sql)
        .contains("uk_ledger_transaction_owner_version")
        .contains("DROP PROCEDURE IF EXISTS platform_db.assert_phase2_v3_preconditions")
        .contains("EXPENSE_WITHHOLDING_TAX")
        .contains("DROP INDEX uk_import_export_content")
        .contains("encryption_key_version")
        .contains("CREATE TABLE ledger_db.ledger_state")
        .contains("CREATE TABLE market_db.option_contract")
        .contains("CREATE TABLE market_db.futures_contract")
        .contains("CREATE TABLE portfolio_db.futures_position")
        .contains("CREATE TABLE platform_db.import_preview")
        .contains("opened_cost_cent BIGINT")
        .contains("remaining_cost_cent BIGINT");
    assertThat(sql.toUpperCase()).doesNotContain("FOREIGN KEY");
  }
}
