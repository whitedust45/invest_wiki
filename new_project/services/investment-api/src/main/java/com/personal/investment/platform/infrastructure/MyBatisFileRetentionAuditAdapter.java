package com.personal.investment.platform.infrastructure;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.platform.application.ExpiredImportExportFile;
import com.personal.investment.platform.application.FileRetentionAuditPort;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class MyBatisFileRetentionAuditAdapter implements FileRetentionAuditPort {
  private final AuditOutboxMapper mapper;
  private final LedgerIdGenerator idGenerator;

  public MyBatisFileRetentionAuditAdapter(AuditOutboxMapper mapper, LedgerIdGenerator idGenerator) {
    this.mapper = mapper;
    this.idGenerator = idGenerator;
  }

  @Override
  public void recordDeleted(ExpiredImportExportFile file) {
    String details = "{\"contentSha256\":\"" + file.contentSha256Hex() + "\",\"reason\":\"RETENTION_30_DAYS\"}";
    mapper.insertAudit(new AuditOutboxMapper.AuditRow(idGenerator.next(), file.ownerUserId(), "IMPORT_EXPORT_FILE",
        file.importExportFileId(), "RETENTION_DELETE", traceId(), details));
  }

  private static String traceId() {
    String traceId = MDC.get("traceId");
    return traceId == null || traceId.isBlank() ? "background" : traceId;
  }
}
