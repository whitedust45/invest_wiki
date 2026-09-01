package com.personal.investment.platform.infrastructure;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.application.LedgerTransactionEventPort;
import com.personal.investment.ledger.domain.LedgerTransaction;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Stores a minimal audit trail and an outbox event in the transaction that appended the ledger fact. */
@Component
public class LedgerAuditOutboxAdapter implements LedgerTransactionEventPort {
  private static final String RESOURCE_TYPE = "LEDGER_TRANSACTION";
  private static final String EVENT_TYPE = "ledger.transaction.appended";

  private final AuditOutboxMapper mapper;
  private final LedgerIdGenerator idGenerator;

  public LedgerAuditOutboxAdapter(AuditOutboxMapper mapper, LedgerIdGenerator idGenerator) {
    this.mapper = mapper;
    this.idGenerator = idGenerator;
  }

  @Override
  public void recordAppended(LedgerTransaction transaction) {
    String transactionId = transaction.transactionId();
    String detailJson = "{\"transactionType\":\"" + transaction.transactionType().name()
        + "\",\"ledgerVersion\":\"" + transaction.ledgerVersion() + "\"}";
    mapper.insertAudit(new AuditOutboxMapper.AuditRow(idGenerator.next(), transaction.ownerUserId(), RESOURCE_TYPE,
        transactionId, "APPEND", traceId(), detailJson));
    mapper.insertOutbox(new AuditOutboxMapper.OutboxRow(idGenerator.next(), RESOURCE_TYPE, transactionId, EVENT_TYPE,
        detailJson));
  }

  private static String traceId() {
    String traceId = MDC.get("traceId");
    return traceId == null || traceId.isBlank() ? "background" : traceId;
  }
}
