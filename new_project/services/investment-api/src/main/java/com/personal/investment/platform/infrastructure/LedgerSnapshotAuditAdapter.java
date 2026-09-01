package com.personal.investment.platform.infrastructure;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.application.LedgerSnapshot;
import com.personal.investment.ledger.application.LedgerSnapshotAuditPort;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class LedgerSnapshotAuditAdapter implements LedgerSnapshotAuditPort {
  private final AuditOutboxMapper mapper;
  private final LedgerIdGenerator idGenerator;

  public LedgerSnapshotAuditAdapter(AuditOutboxMapper mapper, LedgerIdGenerator idGenerator) {
    this.mapper = mapper;
    this.idGenerator = idGenerator;
  }

  @Override
  public void recordGenerated(String ownerUserId, LedgerSnapshot snapshot) {
    String detail = "{\"sourceLedgerVersion\":\"" + snapshot.sourceLedgerVersion() + "\",\"contentSha256\":\""
        + snapshot.contentSha256Hex() + "\"}";
    insert(ownerUserId, snapshot.ledgerSnapshotId(), "GENERATE", detail);
  }

  @Override
  public void recordRestored(String ownerUserId, String ledgerSnapshotId, int restoredAccountCount,
      int restoredTransactionCount, long targetLedgerVersion) {
    String detail = "{\"restoredAccountCount\":\"" + restoredAccountCount + "\",\"restoredTransactionCount\":\""
        + restoredTransactionCount + "\",\"targetLedgerVersion\":\"" + targetLedgerVersion + "\"}";
    insert(ownerUserId, ledgerSnapshotId, "RESTORE_EMPTY_WORKSPACE", detail);
  }

  private void insert(String ownerUserId, String snapshotId, String action, String detail) {
    String traceId = MDC.get("traceId");
    mapper.insertAudit(new AuditOutboxMapper.AuditRow(idGenerator.next(), ownerUserId, "LEDGER_SNAPSHOT", snapshotId,
        action, traceId == null || traceId.isBlank() ? "background" : traceId, detail));
  }
}
