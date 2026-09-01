package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DatabaseGrantPolicyTest {
  @Test
  void flywayGrantMigrationCannotMutateOrDeleteAppendOnlyLedgerFacts() throws Exception {
    String grants = Files.readString(Path.of("src/main/resources/db/migration/V9__grant_application_user_privileges.sql"));

    assertThat(grants)
        .doesNotContain("ON ledger_db.*")
        .doesNotContain("UPDATE, DELETE ON ledger_db.ledger_transaction")
        .doesNotContain("UPDATE, DELETE ON ledger_db.ledger_posting")
        .doesNotContain("UPDATE, DELETE ON ledger_db.ledger_trade_detail")
        .contains("GRANT SELECT, INSERT ON ledger_db.ledger_transaction")
        .contains("GRANT SELECT, INSERT ON ledger_db.ledger_posting");
  }
}
