package com.personal.investment.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.ledger.application.LedgerCommandAccountPort;
import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LegacyImportServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String CNY_CASH = "01K8D43J4YFN7X9R2B6C8M0V31";
  private static final String USD_CASH = "01K8D43J4YFN7X9R2B6C8M0V32";
  private static final String FILE = "01K8D43J4YFN7X9R2B6C8M0V33";
  private static final String QQQ = "01K8D43J4YFN7X9R2B6C8M0V34";

  @Test
  void createsAChecksumProtectedDryRunWithExactWanAndPriceConversions() throws Exception {
    byte[] content = """
        {"ledger":{"entries":[
          {"date":"2026-01-02","module":"cash","action":"deposit","amount":"1.23"},
          {"date":"2026-01-03","module":"qqq","action":"buy","symbol":"QQQ","quantity":"2","price":"6.66","fee":"0.10"}
        ]}}
        """.getBytes(StandardCharsets.UTF_8);
    InMemoryFiles files = new InMemoryFiles(content);
    InMemoryPreviews previews = new InMemoryPreviews();
    LegacyImportService service = service(files, previews, content);

    LegacyImportPreview preview = service.createPreview(OWNER, new CreateLegacyImportPreviewCommand(FILE,
        LegacyImportFormat.LEGACY_DASHBOARD_JSON, null,
        List.of(new LegacyCurrencyMapping("cash", null, CurrencyCode.CNY, LegacyAmountUnit.LEGACY_CNY_WAN, CNY_CASH),
            new LegacyCurrencyMapping("qqq", null, CurrencyCode.USD, LegacyAmountUnit.ORIGINAL_CURRENCY_DECIMAL, USD_CASH)),
        List.of(new LegacyInstrumentMapping("qqq", "QQQ", QQQ)), List.of(), List.of()));

    LegacyImportPreviewPayload payload = new ObjectMapper().readValue(preview.previewJson(), LegacyImportPreviewPayload.class);
    assertThat(preview.status()).isEqualTo(LegacyImportPreviewStatus.SUCCEEDED);
    assertThat(payload.applicableCount()).isEqualTo(2);
    assertThat(payload.needsReviewCount()).isZero();
    assertThat(payload.lines().get(0).amountCent()).isEqualTo("1230000");
    assertThat(payload.lines().get(1).unitPriceCent()).isEqualTo("666");
    assertThat(payload.lines().get(1).feeCent()).isEqualTo("10");
    assertThat(files.status).isEqualTo(ImportExportFileStatus.PREVIEWED);
    assertThat(previews.items).containsExactly(preview);
  }

  @Test
  void mapsConfirmedLegacyRollToFeeOnlyWithoutInventingTradingLegs() throws Exception {
    byte[] content = """
        {"ledger":{"entries":[
          {"date":"2026-01-02","module":"ic","action":"roll","fee":"0.005","note":"legacy move"}
        ]}}
        """.getBytes(StandardCharsets.UTF_8);
    LegacyImportService service = service(new InMemoryFiles(content), new InMemoryPreviews(), content);

    LegacyImportPreview preview = service.createPreview(OWNER, new CreateLegacyImportPreviewCommand(FILE,
        LegacyImportFormat.LEGACY_DASHBOARD_JSON, null,
        List.of(new LegacyCurrencyMapping("ic", null, CurrencyCode.CNY, LegacyAmountUnit.LEGACY_CNY_WAN, CNY_CASH)),
        List.of(), List.of(), List.of()));

    LegacyImportPreviewPayload payload = new ObjectMapper().readValue(preview.previewJson(), LegacyImportPreviewPayload.class);
    assertThat(payload.lines()).singleElement().satisfies(line -> {
      assertThat(line.operation()).isEqualTo(LegacyImportOperation.FEE);
      assertThat(line.amountCent()).isEqualTo("5000");
      assertThat(line.code()).isEqualTo("ROLL_FEE_ONLY");
      assertThat(line.note()).contains("legacy roll: fee-only");
    });
  }

  @Test
  void turnsARejectedRollbackOnlyLedgerDryRunIntoANonConfirmablePreviewRow() throws Exception {
    byte[] content = """
        {"ledger":{"entries":[{"date":"2026-01-02","module":"cash","action":"deposit","amount":"1"}]}}
        """.getBytes(StandardCharsets.UTF_8);
    LegacyImportService service = service(new InMemoryFiles(content), new InMemoryPreviews(), content,
        (ownerUserId, importExportFileId, lines) -> LegacyImportDryRunResult.rejected(1, "balance rule rejected"));

    LegacyImportPreview preview = service.createPreview(OWNER, new CreateLegacyImportPreviewCommand(FILE,
        LegacyImportFormat.LEGACY_DASHBOARD_JSON, null,
        List.of(new LegacyCurrencyMapping("cash", null, CurrencyCode.CNY, LegacyAmountUnit.LEGACY_CNY_WAN, CNY_CASH)),
        List.of(), List.of(), List.of()));

    LegacyImportPreviewPayload payload = new ObjectMapper().readValue(preview.previewJson(), LegacyImportPreviewPayload.class);
    assertThat(preview.status()).isEqualTo(LegacyImportPreviewStatus.NEEDS_REVIEW);
    assertThat(payload.lines()).singleElement().satisfies(line -> {
      assertThat(line.status()).isEqualTo("NEEDS_REVIEW");
      assertThat(line.code()).isEqualTo("LEDGER_DRY_RUN_REJECTED");
      assertThat(line.note()).isEqualTo("balance rule rejected");
    });
  }

  private static LegacyImportService service(InMemoryFiles files, InMemoryPreviews previews, byte[] content) {
    return service(files, previews, content, (ownerUserId, importExportFileId, lines) -> LegacyImportDryRunResult.success());
  }

  private static LegacyImportService service(InMemoryFiles files, InMemoryPreviews previews, byte[] content,
      LegacyImportDryRunPort dryRunPort) {
    LedgerCommandAccountPort accounts = new LedgerCommandAccountPort() {
      @Override
      public Optional<LedgerAccount> findByIdAndOwner(String accountId, String ownerUserId) {
        if (!OWNER.equals(ownerUserId)) return Optional.empty();
        if (CNY_CASH.equals(accountId)) return Optional.of(LedgerAccount.newCash(CNY_CASH, OWNER, "CNY", CurrencyCode.CNY));
        if (USD_CASH.equals(accountId)) return Optional.of(LedgerAccount.newCash(USD_CASH, OWNER, "USD", CurrencyCode.USD));
        return Optional.empty();
      }

      @Override
      public Optional<LedgerAccount> findByOwnerAndCode(String ownerUserId, String accountCode) {
        return Optional.empty();
      }
    };
    UploadedObjectStoragePort storage = new UploadedObjectStoragePort() {
      @Override public UploadedObject read(String objectKey) { return new UploadedObject(content, "application/json", java.util.Map.of()); }
      @Override public void copy(String sourceObjectKey, String destinationObjectKey) { }
      @Override public void delete(String objectKey) { }
    };
    LedgerIdGenerator ids = new LedgerIdGenerator() {
      private int sequence;
      @Override public String next() { return "TEST" + ++sequence; }
    };
    return new LegacyImportService(files, storage, previews, ids, new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC), accounts,
        null, dryRunPort);
  }

  private static final class InMemoryFiles implements ImportExportFilePort {
    private final byte[] content;
    private ImportExportFileStatus status = ImportExportFileStatus.SCANNED;

    private InMemoryFiles(byte[] content) { this.content = content; }
    @Override public void append(ImportExportFile file) { }
    @Override public Optional<ImportExportFile> findOwned(String ownerUserId, String importExportFileId) {
      return OWNER.equals(ownerUserId) && FILE.equals(importExportFileId) ? Optional.of(new ImportExportFile(FILE, OWNER,
          ImportExportFileDirection.IMPORT, "evidence/" + FILE, sha256(content), "application/json", content.length,
          status, "local", Instant.parse("2026-08-28T00:00:00Z"))) : Optional.empty();
    }
    @Override public void transition(String ownerUserId, String importExportFileId, ImportExportFileStatus from,
        ImportExportFileStatus to) { assertThat(status).isEqualTo(from); status = to; }
  }

  private static final class InMemoryPreviews implements LegacyImportPreviewPort {
    private final List<LegacyImportPreview> items = new ArrayList<>();
    @Override public void append(LegacyImportPreview preview) { items.add(preview); }
    @Override public Optional<LegacyImportPreview> findOwned(String ownerUserId, String jobId) {
      return items.stream().filter(item -> item.ownerUserId().equals(ownerUserId) && item.jobId().equals(jobId)).findFirst();
    }
    @Override public Optional<LegacyImportPreview> lockOwned(String ownerUserId, String jobId) { return findOwned(ownerUserId, jobId); }
    @Override public void expireUncommitted(String ownerUserId, String importExportFileId) { }
    @Override public void markCommitted(String ownerUserId, String jobId) { }
  }

  private static String sha256(byte[] content) {
    try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
    catch (Exception exception) { throw new AssertionError(exception); }
  }
}
