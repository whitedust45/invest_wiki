package com.personal.investment.ledger.application;

import com.personal.investment.platform.application.ImportExportFile;
import com.personal.investment.platform.application.ImportExportFileDirection;
import com.personal.investment.platform.application.ImportExportFilePort;
import com.personal.investment.platform.application.ImportExportFileStatus;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates and reads immutable, encrypted ledger snapshot artifacts. It never restores or rewrites any facts; the
 * separate recovery service has the intentionally narrow empty-workspace gate.
 */
@Service
public class LedgerSnapshotService {
  private static final Instant SNAPSHOT_RETENTION_SENTINEL = Instant.parse("9999-12-31T23:59:59.999Z");
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

  private final LedgerExportService exportService;
  private final LedgerSnapshotPort snapshotPort;
  private final LedgerSnapshotStoragePort storagePort;
  private final ImportExportFilePort filePort;
  private final LedgerIdGenerator idGenerator;
  private final LedgerSnapshotAuditPort auditPort;
  private final Clock clock;
  private final com.personal.investment.bootstrap.config.ObjectStorageProperties storageProperties;
  private final TransactionTemplate batchItemTransaction;

  @Autowired
  public LedgerSnapshotService(LedgerExportService exportService, LedgerSnapshotPort snapshotPort,
      LedgerSnapshotStoragePort storagePort, ImportExportFilePort filePort, LedgerIdGenerator idGenerator,
      LedgerSnapshotAuditPort auditPort, Clock clock,
      com.personal.investment.bootstrap.config.ObjectStorageProperties storageProperties,
      PlatformTransactionManager transactionManager) {
    this(exportService, snapshotPort, storagePort, filePort, idGenerator, auditPort, clock, storageProperties,
        requiresNew(transactionManager));
  }

  /** Narrow constructor retained for unit tests that only exercise artifact validation and download. */
  LedgerSnapshotService(LedgerExportService exportService, LedgerSnapshotPort snapshotPort,
      LedgerSnapshotStoragePort storagePort, ImportExportFilePort filePort, LedgerIdGenerator idGenerator,
      LedgerSnapshotAuditPort auditPort, Clock clock,
      com.personal.investment.bootstrap.config.ObjectStorageProperties storageProperties) {
    this(exportService, snapshotPort, storagePort, filePort, idGenerator, auditPort, clock, storageProperties,
        (TransactionTemplate) null);
  }

  private LedgerSnapshotService(LedgerExportService exportService, LedgerSnapshotPort snapshotPort,
      LedgerSnapshotStoragePort storagePort, ImportExportFilePort filePort, LedgerIdGenerator idGenerator,
      LedgerSnapshotAuditPort auditPort, Clock clock,
      com.personal.investment.bootstrap.config.ObjectStorageProperties storageProperties,
      TransactionTemplate batchItemTransaction) {
    this.exportService = exportService;
    this.snapshotPort = snapshotPort;
    this.storagePort = storagePort;
    this.filePort = filePort;
    this.idGenerator = idGenerator;
    this.auditPort = auditPort;
    this.clock = clock;
    this.storageProperties = storageProperties;
    this.batchItemTransaction = batchItemTransaction;
  }

  @Transactional
  public LedgerSnapshot create(String ownerUserId) {
    requireOwner(ownerUserId);
    LedgerExportFile export = exportService.generate(ownerUserId, LedgerExportFormat.JSON);
    if (export.sourceLedgerVersion() < 1) {
      throw new IllegalArgumentException("an empty ledger has no snapshot to create");
    }
    LocalDate asOfDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
    return snapshotPort.findOwnedAtVersion(ownerUserId, asOfDate, export.sourceLedgerVersion())
        .orElseGet(() -> persist(ownerUserId, asOfDate, export));
  }

  @Transactional(readOnly = true)
  public List<LedgerSnapshot> list(String ownerUserId, int limit) {
    requireOwner(ownerUserId);
    return snapshotPort.findOwnedRecent(ownerUserId, limit);
  }

  @Transactional(readOnly = true)
  public LedgerSnapshotFile download(String ownerUserId, String ledgerSnapshotId) {
    requireOwner(ownerUserId);
    LedgerSnapshot snapshot = snapshotPort.findOwned(ownerUserId, ledgerSnapshotId)
        .orElseThrow(() -> new IllegalArgumentException("ledger snapshot was not found"));
    ImportExportFile file = filePort.findOwned(ownerUserId, snapshot.importExportFileId())
        .orElseThrow(() -> new IllegalStateException("ledger snapshot artifact metadata is missing"));
    String expectedKey = objectKey(ownerUserId, snapshot.ledgerSnapshotId());
    if (file.direction() != ImportExportFileDirection.SNAPSHOT || file.status() != ImportExportFileStatus.COMMITTED
        || !expectedKey.equals(file.objectKey()) || !snapshot.contentSha256Hex().equals(file.contentSha256Hex())) {
      throw new IllegalStateException("ledger snapshot artifact metadata is inconsistent");
    }
    byte[] content = storagePort.read(expectedKey);
    if (!snapshot.contentSha256Hex().equals(sha256(content))) {
      throw new IllegalStateException("ledger snapshot content checksum does not match metadata");
    }
    return new LedgerSnapshotFile(snapshot, content);
  }

  /** The scheduled job takes one artifact per owner/day/source-version and leaves older artifacts immutable. */
  public SnapshotBatchResult createForAllOwners() {
    int createdOrExisting = 0;
    int failures = 0;
    for (String ownerUserId : snapshotPort.findOwnersWithLedgerFacts()) {
      try {
        if (batchItemTransaction == null) {
          throw new IllegalStateException("scheduled ledger snapshots require a transaction manager");
        }
        batchItemTransaction.executeWithoutResult(status -> create(ownerUserId));
        createdOrExisting++;
      } catch (RuntimeException ignored) {
        failures++;
      }
    }
    return new SnapshotBatchResult(createdOrExisting, failures);
  }

  private LedgerSnapshot persist(String ownerUserId, LocalDate asOfDate, LedgerExportFile export) {
    String snapshotId = idGenerator.next();
    String artifactFileId = idGenerator.next();
    String checksum = export.contentSha256Hex();
    String objectKey = objectKey(ownerUserId, snapshotId);
    LedgerSnapshot snapshot = new LedgerSnapshot(snapshotId, ownerUserId, asOfDate, export.sourceLedgerVersion(),
        artifactFileId, checksum, clock.instant());
    storagePort.write(objectKey, export.content(), checksum);
    try {
      filePort.append(new ImportExportFile(artifactFileId, ownerUserId, ImportExportFileDirection.SNAPSHOT, objectKey,
          checksum, "application/json", export.content().length, ImportExportFileStatus.COMMITTED,
          storageProperties.encryptionKeyVersion(), SNAPSHOT_RETENTION_SENTINEL));
      snapshotPort.append(snapshot);
      auditPort.recordGenerated(ownerUserId, snapshot);
      return snapshot;
    } catch (RuntimeException exception) {
      try {
        storagePort.delete(objectKey);
      } catch (RuntimeException ignored) {
        // The object is still private and content-addressed by an uncommitted key; an operational scavenger can remove it.
      }
      throw exception;
    }
  }

  private static String objectKey(String ownerUserId, String snapshotId) {
    return "snapshots/" + ownerUserId + "/" + snapshotId + ".json";
  }

  private static TransactionTemplate requiresNew(PlatformTransactionManager transactionManager) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static void requireOwner(String ownerUserId) {
    if (ownerUserId == null || !ownerUserId.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
      throw new IllegalArgumentException("ledger snapshot owner is invalid");
    }
  }

  public record SnapshotBatchResult(int createdOrExisting, int failures) { }
}
