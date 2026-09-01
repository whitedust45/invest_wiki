package com.personal.investment.ledger.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.ledger.domain.LedgerAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produces a portable, owner-scoped audit export. It is a read-only projection: no export can edit, correct,
 * roll back or otherwise replace existing immutable facts.
 */
@Service
public class LedgerExportService {
  private static final int PAGE_SIZE = 100;

  private final LedgerAccountService accountService;
  private final LedgerTransactionQueryService queryService;
  private final LedgerTransactionDetailService detailService;
  private final LedgerIdGenerator idGenerator;
  private final LedgerExportAuditPort auditPort;
  private final ObjectMapper json;
  private final Clock clock;

  public LedgerExportService(LedgerAccountService accountService, LedgerTransactionQueryService queryService,
      LedgerTransactionDetailService detailService, LedgerIdGenerator idGenerator, LedgerExportAuditPort auditPort,
      ObjectMapper json, Clock clock) {
    this.accountService = accountService;
    this.queryService = queryService;
    this.detailService = detailService;
    this.idGenerator = idGenerator;
    this.auditPort = auditPort;
    this.json = json;
    this.clock = clock;
  }

  // Export content is read-only, but each export must append an audit event in the same transaction.
  @Transactional
  public LedgerExportFile generate(String ownerUserId, LedgerExportFormat format) {
    if (ownerUserId == null || !ownerUserId.matches("^[0-9A-HJKMNP-TV-Z]{26}$") || format == null) {
      throw new IllegalArgumentException("ledger export request is invalid");
    }
    List<LedgerTransactionDetail> details = allDetails(ownerUserId);
    long sourceLedgerVersion = details.stream().mapToLong(LedgerTransactionDetail::ledgerVersion).max().orElse(0L);
    String exportId = idGenerator.next();
    byte[] content = switch (format) {
      case JSON -> json(ownerUserId, exportId, sourceLedgerVersion, details);
      case CSV -> csv(details);
    };
    String checksum = sha256(content);
    auditPort.recordGenerated(ownerUserId, exportId, format, checksum, content.length, sourceLedgerVersion);
    return new LedgerExportFile(exportId, format, content, checksum, sourceLedgerVersion);
  }

  private List<LedgerTransactionDetail> allDetails(String ownerUserId) {
    List<LedgerTransactionDetail> details = new ArrayList<>();
    String cursor = null;
    do {
      LedgerTransactionPage page = queryService.list(ownerUserId, cursor, PAGE_SIZE, null, null,
          null, null, null, null, null);
      for (LedgerTransactionSummary summary : page.items()) {
        details.add(detailService.find(ownerUserId, summary.transactionId())
            .orElseThrow(() -> new IllegalStateException("ledger export encountered a transaction outside its owner")));
      }
      cursor = page.nextCursor();
    } while (cursor != null);
    return List.copyOf(details);
  }

  private byte[] json(String ownerUserId, String exportId, long sourceLedgerVersion,
      List<LedgerTransactionDetail> details) {
    List<LedgerExportDocument.Account> accounts = accountService.listAllAccounts(ownerUserId).stream()
        .map(LedgerExportService::account).toList();
    LedgerExportDocument document = new LedgerExportDocument("2", exportId, clock.instant().toString(),
        Long.toString(sourceLedgerVersion), accounts, details.stream().map(LedgerExportService::transaction).toList());
    try {
      return json.writeValueAsBytes(document);
    } catch (Exception exception) {
      throw new IllegalStateException("ledger JSON export could not be encoded", exception);
    }
  }

  private static byte[] csv(List<LedgerTransactionDetail> details) {
    StringBuilder output = new StringBuilder();
    output.append("transaction_id,transaction_type,occurred_on,strategy_key,source_type,import_export_file_id,")
        .append("correction_root_transaction_id,reversal_of_transaction_id,revision_no,ledger_version,note,")
        .append("posting_count,trade_detail_count\n");
    for (LedgerTransactionDetail detail : details) {
      row(output, detail.transactionId(), detail.transactionType().name(), detail.occurredOn().toString(),
          detail.strategyKey(), detail.sourceType().name(), detail.importExportFileId(),
          detail.correctionRootTransactionId(), detail.reversalOfTransactionId(), Integer.toString(detail.revisionNo()),
          Long.toString(detail.ledgerVersion()), detail.note(), Integer.toString(detail.postings().size()),
          Integer.toString(detail.tradeDetails().size()));
    }
    return output.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static void row(StringBuilder output, String... values) {
    for (int index = 0; index < values.length; index++) {
      if (index > 0) {
        output.append(',');
      }
      String value = values[index];
      if (value == null) {
        continue;
      }
      output.append('"').append(value.replace("\"", "\"\"")).append('"');
    }
    output.append('\n');
  }

  private static LedgerExportDocument.Account account(LedgerAccount value) {
    return new LedgerExportDocument.Account(value.accountId(), value.accountCode(), value.displayName(), value.accountKind().name(),
        value.currency().name(), value.status().name(), Long.toString(value.version()));
  }

  private static LedgerExportDocument.Transaction transaction(LedgerTransactionDetail value) {
    return new LedgerExportDocument.Transaction(value.transactionId(), value.transactionType().name(), value.occurredOn().toString(),
        value.strategyKey(), value.operationGroupKey(), value.sourceType().name(), value.importExportFileId(), value.correctionRootTransactionId(),
        value.reversalOfTransactionId(), Integer.toString(value.revisionNo()), Long.toString(value.ledgerVersion()),
        value.note(), value.postings().stream().map(posting -> new LedgerExportDocument.Posting(posting.postingId(), posting.accountId(),
            Integer.toString(posting.postingNo()), posting.postingSide().name(), Long.toString(posting.amountCent()),
            posting.currency().name())).toList(), value.tradeDetails().stream().map(detail -> new LedgerExportDocument.TradeDetail(
            detail.tradeDetailId(), Integer.toString(detail.detailNo()), detail.instrumentId(), detail.positionEffect().name(),
            detail.quantity().toPlainString(), decimal(detail.unitPriceCent()), decimal(detail.pricePoints()),
            decimal(detail.contractMultiplierCent()), detail.deliveryDate() == null ? null : detail.deliveryDate().toString(),
            Long.toString(detail.feeCent()), decimal(detail.optionContractMultiplier()))).toList(),
        value.corporateAction() == null ? null : new LedgerExportDocument.CorporateAction(value.corporateAction().corporateActionId(),
            value.corporateAction().instrumentId(), value.corporateAction().actionType(),
            value.corporateAction().effectiveOn().toString(), Long.toString(value.corporateAction().ratioNumerator()),
            Long.toString(value.corporateAction().ratioDenominator())), value.income() == null ? null
                : new LedgerExportDocument.Income(value.income().incomeDetailId(), value.income().incomeType(),
                    value.income().instrumentId(), value.income().entitlementDate() == null ? null
                        : value.income().entitlementDate().toString(), Long.toString(value.income().grossAmountCent()),
                    Long.toString(value.income().taxWithheldCent()), decimal(value.income().perShareAmountCent()),
                    value.income().currency().name()));
  }

  private static String decimal(Object value) {
    return value == null ? null : value.toString();
  }

  private static String sha256(byte[] content) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
      StringBuilder text = new StringBuilder(64);
      for (byte value : digest) {
        text.append(String.format("%02x", value));
      }
      return text.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

}
