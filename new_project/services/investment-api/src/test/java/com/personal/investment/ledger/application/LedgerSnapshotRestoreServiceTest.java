package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.bootstrap.config.ObjectStorageProperties;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.platform.application.ImportExportFile;
import com.personal.investment.platform.application.ImportExportFileDirection;
import com.personal.investment.platform.application.ImportExportFilePort;
import com.personal.investment.platform.application.ImportExportFileStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LedgerSnapshotRestoreServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String SNAPSHOT = "01K8D43J4YFN7X9R2B6C8M0V31";
  private static final String FILE = "01K8D43J4YFN7X9R2B6C8M0V32";
  private static final String CASH = "01K8D43J4YFN7X9R2B6C8M0V33";
  private static final String EQUITY = "01K8D43J4YFN7X9R2B6C8M0V34";
  private static final String TRANSACTION = "01K8D43J4YFN7X9R2B6C8M0V35";
  private static final String INSTRUMENT = "01K8D43J4YFN7X9R2B6C8M0V36";

  @Test
  void restoresOnlyIntoAnEmptyWorkspaceWithFreshSemanticIdsAndNoMonetaryCoercion() throws Exception {
    byte[] content = snapshotJson();
    InMemoryAccounts accounts = new InMemoryAccounts(false);
    InMemoryTransactions transactions = new InMemoryTransactions(false);
    List<String> audit = new ArrayList<>();
    LedgerSnapshotRestoreService service = service(content, accounts, transactions, audit);

    LedgerSnapshotRestoreResult result = service.restoreIntoEmptyWorkspace(OWNER, SNAPSHOT);

    assertThat(result.restoredAccountCount()).isEqualTo(2);
    assertThat(result.restoredTransactionCount()).isEqualTo(1);
    assertThat(result.targetLedgerVersion()).isEqualTo(1);
    assertThat(accounts.items).allSatisfy(account -> assertThat(account.ownerUserId()).isEqualTo(OWNER));
    assertThat(accounts.items).extracting(LedgerAccount::accountCode).contains("CASH:" + accounts.items.getFirst().accountId());
    assertThat(transactions.items).singleElement().satisfies(transaction -> {
      assertThat(transaction.transactionId()).isNotEqualTo(TRANSACTION);
      assertThat(transaction.ledgerVersion()).isEqualTo(1);
      assertThat(transaction.postings()).hasSize(2);
      assertThat(transaction.postings()).allSatisfy(posting -> assertThat(posting.amount().cent()).isEqualTo(666L));
    });
    assertThat(audit).containsExactly("restore:2:1:1");
  }

  @Test
  void rejectsRestoreBeforeItCanAppendAnythingWhenTheWorkspaceAlreadyContainsFacts() throws Exception {
    byte[] content = snapshotJson();
    InMemoryAccounts accounts = new InMemoryAccounts(true);
    InMemoryTransactions transactions = new InMemoryTransactions(true);
    LedgerSnapshotRestoreService service = service(content, accounts, transactions, new ArrayList<>());

    assertThatThrownBy(() -> service.restoreIntoEmptyWorkspace(OWNER, SNAPSHOT))
        .isInstanceOf(LedgerSnapshotRestoreRejectedException.class)
        .hasMessageContaining("empty ledger workspace");
    assertThat(accounts.items).isEmpty();
    assertThat(transactions.items).isEmpty();
  }

  private static LedgerSnapshotRestoreService service(byte[] content, InMemoryAccounts accounts,
      InMemoryTransactions transactions, List<String> audit) {
    LedgerIdGenerator ids = new SequenceIds();
    LedgerSnapshot snapshot = new LedgerSnapshot(SNAPSHOT, OWNER, LocalDate.of(2026, 8, 1), 1L, FILE,
        sha256(content), Instant.parse("2026-08-01T00:00:00Z"));
    LedgerSnapshotPort snapshots = new LedgerSnapshotPort() {
      @Override public void append(LedgerSnapshot ignored) { }
      @Override public Optional<LedgerSnapshot> findOwned(String ownerUserId, String snapshotId) {
        return OWNER.equals(ownerUserId) && SNAPSHOT.equals(snapshotId) ? Optional.of(snapshot) : Optional.empty();
      }
      @Override public Optional<LedgerSnapshot> findOwnedAtVersion(String ownerUserId, LocalDate asOfDate, long version) {
        return Optional.empty();
      }
      @Override public List<LedgerSnapshot> findOwnedRecent(String ownerUserId, int limit) { return List.of(); }
      @Override public List<String> findOwnersWithLedgerFacts() { return List.of(); }
    };
    ImportExportFilePort files = new ImportExportFilePort() {
      @Override public void append(ImportExportFile ignored) { }
      @Override public Optional<ImportExportFile> findOwned(String ownerUserId, String fileId) {
        return Optional.of(new ImportExportFile(FILE, OWNER, ImportExportFileDirection.SNAPSHOT,
            "snapshots/" + OWNER + "/" + SNAPSHOT + ".json", sha256(content), "application/json", content.length,
            ImportExportFileStatus.COMMITTED, "test", Instant.parse("9999-12-31T23:59:59.999Z")));
      }
    };
    LedgerSnapshotStoragePort storage = new LedgerSnapshotStoragePort() {
      @Override public void write(String objectKey, byte[] bytes, String checksum) { }
      @Override public byte[] read(String objectKey) { return content; }
      @Override public void delete(String objectKey) { }
    };
    LedgerSnapshotAuditPort auditPort = new LedgerSnapshotAuditPort() {
      @Override public void recordGenerated(String ownerUserId, LedgerSnapshot ignored) { }
      @Override public void recordRestored(String ownerUserId, String snapshotId, int accountCount,
          int transactionCount, long targetVersion) {
        audit.add("restore:" + accountCount + ":" + transactionCount + ":" + targetVersion);
      }
    };
    LedgerSnapshotService snapshotService = new LedgerSnapshotService(null, snapshots, storage, files, ids, auditPort,
        Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC), storageProperties());
    Instrument instrument = Instrument.newActive(INSTRUMENT, "US", "NASDAQ", "TEST", "测试标的", AssetType.EQUITY,
        CurrencyCode.USD, null, null, null);
    InstrumentPort instruments = new InstrumentPort() {
      @Override public Optional<Instrument> findByNaturalKey(String market, String exchange, String symbol) { return Optional.empty(); }
      @Override public Optional<Instrument> findById(String instrumentId) {
        return INSTRUMENT.equals(instrumentId) ? Optional.of(instrument) : Optional.empty();
      }
      @Override public void insert(Instrument value, String futureContractId, String optionContractId) { }
    };
    return new LedgerSnapshotRestoreService(snapshotService, accounts, transactions, detail -> { }, detail -> { },
        instruments, ids, auditPort, new ObjectMapper());
  }

  private static byte[] snapshotJson() throws Exception {
    LedgerExportDocument document = new LedgerExportDocument("2", "01K8D43J4YFN7X9R2B6C8M0V37",
        "2026-08-01T00:00:00Z", "1", List.of(
            new LedgerExportDocument.Account(CASH, "CASH:" + CASH, "美元现金", LedgerAccountKind.ASSET_CASH.name(),
                "USD", LedgerAccountStatus.ACTIVE.name(), "0"),
            new LedgerExportDocument.Account(EQUITY, "SYS:EXTERNAL_EQUITY:USD", "外部资金", LedgerAccountKind.EQUITY_EXTERNAL.name(),
                "USD", LedgerAccountStatus.ACTIVE.name(), "0")), List.of(
            new LedgerExportDocument.Transaction(TRANSACTION, "EXTERNAL_FUNDING", "2026-08-01", null, null,
                "MANUAL", null, TRANSACTION, null, "0", "1", "initial funding", List.of(
                    new LedgerExportDocument.Posting("01K8D43J4YFN7X9R2B6C8M0V38", CASH, "1", "DEBIT", "666", "USD"),
                    new LedgerExportDocument.Posting("01K8D43J4YFN7X9R2B6C8M0V39", EQUITY, "2", "CREDIT", "666", "USD")),
                List.of(), null, null)));
    return new ObjectMapper().writeValueAsBytes(document);
  }

  private static ObjectStorageProperties storageProperties() {
    return new ObjectStorageProperties("http://example.test", "http://example.test", "private", "access", "secret",
        "test", 1_000_000L, Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofMinutes(1), 10);
  }

  private static String sha256(byte[] content) {
    try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content)); }
    catch (Exception exception) { throw new AssertionError(exception); }
  }

  private static final class InMemoryAccounts implements LedgerAccountPort {
    private final boolean nonEmpty;
    private final List<LedgerAccount> items = new ArrayList<>();
    private InMemoryAccounts(boolean nonEmpty) { this.nonEmpty = nonEmpty; }
    @Override public void insert(LedgerAccount account) { items.add(account); }
    @Override public void insertSystemIfAbsent(LedgerAccount account) { }
    @Override public List<LedgerAccount> findCashAccountsByOwner(String ownerUserId) { return List.of(); }
    @Override public boolean hasAnyAccountByOwner(String ownerUserId) { return nonEmpty; }
  }

  private static final class InMemoryTransactions implements LedgerTransactionPort {
    private final boolean nonEmpty;
    private final List<LedgerTransaction> items = new ArrayList<>();
    private InMemoryTransactions(boolean nonEmpty) { this.nonEmpty = nonEmpty; }
    @Override public long lockCurrentLedgerVersion(String ownerUserId, String stateId) { return 0; }
    @Override public long reserveNextLedgerVersion(String ownerUserId, long version) { return version + 1; }
    @Override public List<com.personal.investment.ledger.domain.LedgerPostingFact> findPostingFactsByOwner(String ownerUserId) { return List.of(); }
    @Override public boolean hasAnyTransactionByOwner(String ownerUserId) { return nonEmpty; }
    @Override public void append(LedgerTransaction transaction) { items.add(transaction); }
  }

  private static final class SequenceIds implements LedgerIdGenerator {
    private int sequence;
    @Override public String next() { return "01K8D43J4YFN7X9R2B6C8M" + String.format("%04d", ++sequence); }
  }
}
