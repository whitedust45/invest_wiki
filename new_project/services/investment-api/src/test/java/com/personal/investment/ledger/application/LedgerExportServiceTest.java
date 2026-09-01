package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import com.personal.investment.ledger.domain.LedgerSourceType;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.PositionEffect;
import com.personal.investment.ledger.domain.PostingSide;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LedgerExportServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String ACCOUNT = "01K8D43J4YFN7X9R2B6C8M0V3A";
  private static final String TRANSACTION = "01K8D43J4YFN7X9R2B6C8M0V31";
  private static final String INSTRUMENT = "01K8D43J4YFN7X9R2B6C8M0V3E";

  @Test
  void producesMoneySafeJsonAndAuditsItsChecksumWithoutMutatingFacts() {
    List<String> audit = new ArrayList<>();
    LedgerExportService service = service(audit);

    LedgerExportFile file = service.generate(OWNER, LedgerExportFormat.JSON);
    String text = new String(file.content(), StandardCharsets.UTF_8);

    assertThat(text).contains("\"schemaVersion\":\"2\"");
    assertThat(text).contains("\"amountCent\":\"666\"");
    assertThat(text).contains("\"unitPriceCent\":\"333\"");
    assertThat(text).doesNotContain("\"amountCent\":666");
    assertThat(file.sourceLedgerVersion()).isEqualTo(7L);
    assertThat(audit).singleElement().satisfies(value ->
        assertThat(value).contains("JSON", file.contentSha256Hex(), "7"));
  }

  @Test
  void producesEscapedCsvRowsForSpreadsheetInspection() {
    LedgerExportFile file = service(new ArrayList<>()).generate(OWNER, LedgerExportFormat.CSV);
    String text = new String(file.content(), StandardCharsets.UTF_8);

    assertThat(text).startsWith("transaction_id,transaction_type");
    assertThat(text).contains("\"TRADE_BUY\"");
    assertThat(text).contains("\"quoted \"\"note\"\"\"");
  }

  private static LedgerExportService service(List<String> audit) {
    LedgerTransactionSummary summary = new LedgerTransactionSummary(TRANSACTION, LedgerTransactionType.TRADE_BUY,
        LocalDate.of(2026, 8, 1), CurrencyCode.USD, 7L);
    LedgerTransactionQueryService query = new LedgerTransactionQueryService(new LedgerTransactionQueryPort() {
      @Override
      public List<LedgerTransactionSummary> find(String ownerUserId, TransactionCursor cursor, int limit,
          String accountId, String instrumentId, LedgerTransactionType transactionType, String strategyKey,
          String search, LocalDate from, LocalDate to) {
        return List.of(summary);
      }
    });
    LedgerTransactionDetail detail = new LedgerTransactionDetail(TRANSACTION, LedgerTransactionType.TRADE_BUY,
        LocalDate.of(2026, 8, 1), null, null, LedgerSourceType.MANUAL, null, TRANSACTION, null, 0, 7L,
        "quoted \"note\"", true, List.of(new LedgerTransactionDetail.Posting("01K8D43J4YFN7X9R2B6C8M0V41",
            ACCOUNT, 1, PostingSide.DEBIT, 666L, CurrencyCode.USD)),
        List.of(new LedgerTransactionDetail.TradeDetail("01K8D43J4YFN7X9R2B6C8M0V42", 1, INSTRUMENT,
            PositionEffect.OPEN, BigDecimal.valueOf(2L), 333L, null, null, null, 0L, null)), null, null);
    LedgerTransactionDetailService details = new LedgerTransactionDetailService((ownerUserId, transactionId) ->
        Optional.of(detail));
    LedgerAccountService accounts = new LedgerAccountService(new LedgerAccountPort() {
      @Override
      public void insert(LedgerAccount account) {
      }

      @Override
      public void insertSystemIfAbsent(LedgerAccount account) {
      }

      @Override
      public List<LedgerAccount> findCashAccountsByOwner(String ownerUserId) {
        return List.of(new LedgerAccount(ACCOUNT, OWNER, "CASH:" + ACCOUNT, LedgerAccountKind.ASSET_CASH,
            CurrencyCode.USD, "美元账户", LedgerAccountStatus.ACTIVE, 3L));
      }
    }, () -> "01K8D43J4YFN7X9R2B6C8M0V51");
    LedgerExportAuditPort auditPort = (ownerUserId, exportId, format, checksum, byteSize, sourceVersion) ->
        audit.add(format.name() + ":" + checksum + ":" + byteSize + ":" + sourceVersion);
    return new LedgerExportService(accounts, query, details, () -> "01K8D43J4YFN7X9R2B6C8M0V61", auditPort,
        new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC));
  }
}
