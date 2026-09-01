package com.personal.investment.platform.infrastructure;

import com.personal.investment.ledger.application.LedgerExportAuditPort;
import com.personal.investment.ledger.application.LedgerExportFormat;
import com.personal.investment.ledger.application.LedgerIdGenerator;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Appends a minimal, content-free audit row for every generated private export. */
@Component
public class LedgerExportAuditAdapter implements LedgerExportAuditPort {
  private final AuditOutboxMapper mapper;
  private final LedgerIdGenerator idGenerator;

  public LedgerExportAuditAdapter(AuditOutboxMapper mapper, LedgerIdGenerator idGenerator) {
    this.mapper = mapper;
    this.idGenerator = idGenerator;
  }

  @Override
  public void recordGenerated(String ownerUserId, String exportId, LedgerExportFormat format, String contentSha256Hex,
      long byteSize, long sourceLedgerVersion) {
    String detail = "{\"format\":\"" + format.name() + "\",\"contentSha256\":\"" + contentSha256Hex
        + "\",\"byteSize\":\"" + byteSize + "\",\"sourceLedgerVersion\":\"" + sourceLedgerVersion + "\"}";
    mapper.insertAudit(new AuditOutboxMapper.AuditRow(idGenerator.next(), ownerUserId, "LEDGER_EXPORT", exportId,
        "GENERATE", traceId(), detail));
  }

  private static String traceId() {
    String traceId = MDC.get("traceId");
    return traceId == null || traceId.isBlank() ? "background" : traceId;
  }
}
