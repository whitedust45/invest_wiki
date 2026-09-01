package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V11StrategyWorkspaceSchemaSpecificationTest {
  private static final Path V11 = Path.of(
      "src/main/resources/db/migration/V11__strategy_workspace_and_ledger_attribution.sql");

  @Test
  void createsAppendOnlyStrategyWorkspaceStorageAndSemanticLedgerAttributionWithoutForeignKeys()
      throws Exception {
    assertThat(V11).exists();
    String sql = Files.readString(V11);

    assertThat(sql)
        .contains("ADD COLUMN owner_user_id")
        .contains("strategy_active_rule")
        .contains("strategy_active_rule_event")
        .contains("strategy_reference_nav")
        .contains("strategy_seed_run")
        .contains("strategy_evaluation_id")
        .contains("signal_scope")
        .contains("ADD COLUMN strategy_key")
        .contains("idx_ledger_transaction_strategy")
        .contains("uk_strategy_evaluation_input")
        .contains("uk_strategy_signal_evaluation_key");
    assertThat(sql.toUpperCase()).doesNotContain("FOREIGN KEY");
  }
}
