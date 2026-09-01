package com.personal.investment.platform.application;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Component
public class LegacyImportDryRunExecutor implements LegacyImportDryRunPort {
  private final LegacyImportLedgerAppender appender;

  public LegacyImportDryRunExecutor(LegacyImportLedgerAppender appender) {
    this.appender = appender;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public LegacyImportDryRunResult validate(String ownerUserId, String importExportFileId,
      List<LegacyImportPreviewLine> lines) {
    int row = 0;
    try {
      for (LegacyImportPreviewLine line : lines.stream().filter(item -> "APPLICABLE".equals(item.status()))
          .sorted(Comparator.comparing(LegacyImportPreviewLine::occurredOn).thenComparingInt(LegacyImportPreviewLine::sourceRow))
          .toList()) {
        row = line.sourceRow();
        appender.append(ownerUserId, importExportFileId, line);
      }
      return LegacyImportDryRunResult.success();
    } catch (RuntimeException exception) {
      return LegacyImportDryRunResult.rejected(row, exception.getMessage());
    } finally {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
  }
}
