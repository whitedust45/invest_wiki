package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V13OptionExpiryTradeDetailSchemaSpecificationTest {
  private static final Path V13 = Path.of("src/main/resources/db/migration/V13__allow_worthless_option_expiry_trade_detail.sql");

  @Test
  void permitsOnlyTheExplicitNoPriceOptionExpiryBranch() throws Exception {
    String sql = Files.readString(V13);

    assertThat(sql).contains("DROP CHECK ledger_trade_detail_chk_3");
    assertThat(sql).contains("ck_ledger_trade_detail_price_or_option_expiry");
    assertThat(sql).contains("option_contract_multiplier IS NOT NULL");
  }
}
