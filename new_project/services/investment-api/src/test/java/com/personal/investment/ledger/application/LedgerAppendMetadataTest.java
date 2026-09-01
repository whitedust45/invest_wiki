package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LedgerAppendMetadataTest {
  @Test
  void keepsStrategyAttributionWhenAFuturesRollAddsAnOperationGroupAndCleansUpAfterward() {
    String observed = LedgerAppendMetadata.withStrategyKey("IC_IM", () ->
        LedgerAppendMetadata.withOperationGroupKey("01K8D43J4YFN7X9R2B6C8M0V3G", () ->
            LedgerAppendMetadata.strategyKey() + ":" + LedgerAppendMetadata.operationGroupKey()));

    assertThat(observed).isEqualTo("IC_IM:01K8D43J4YFN7X9R2B6C8M0V3G");
    assertThat(LedgerAppendMetadata.strategyKey()).isNull();
    assertThat(LedgerAppendMetadata.operationGroupKey()).isNull();
  }
}
