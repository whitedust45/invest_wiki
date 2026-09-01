package com.personal.investment.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerImportedTransactionTest {
  @Test
  void importedFactAlwaysCarriesTheEvidenceFileBusinessId() {
    LedgerTransaction transaction = LedgerTransaction.imported("TX", "OWNER", LedgerTransactionType.EXTERNAL_FUNDING,
        LocalDate.parse("2026-01-02"), "01K8D43J4YFN7X9R2B6C8M0V33", 1, "import", List.of(
            new LedgerPostingFact("P1", "CASH", 1, PostingSide.DEBIT, Money.of(100, CurrencyCode.USD)),
            new LedgerPostingFact("P2", "EQUITY", 2, PostingSide.CREDIT, Money.of(100, CurrencyCode.USD))));

    assertThat(transaction.sourceType()).isEqualTo(LedgerSourceType.IMPORT);
    assertThat(transaction.importExportFileId()).isEqualTo("01K8D43J4YFN7X9R2B6C8M0V33");
  }
}
