package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V12StrategyScanSchemaSpecificationTest {
  private static final Path V12 = Path.of("src/main/resources/db/migration/V12__strategy_scan_queue.sql");

  @Test
  void persistsSemanticScanAndItemIdsWithoutForeignKeysOrGenericBusinessIds() throws Exception {
    assertThat(V12).exists();
    String sql = Files.readString(V12);

    assertThat(sql)
        .contains("strategy_scan_id")
        .contains("strategy_scan_item_id")
        .contains("requested_strategy_keys_json")
        .contains("uk_strategy_scan_item_scan_key")
        .contains("GRANT SELECT, INSERT, UPDATE ON strategy_db.strategy_scan")
        .contains("GRANT SELECT, INSERT ON strategy_db.strategy_scan_item");
    assertThat(sql.toUpperCase()).doesNotContain("FOREIGN KEY");
    assertThat(sql).doesNotContain("biz_id");
  }
}
