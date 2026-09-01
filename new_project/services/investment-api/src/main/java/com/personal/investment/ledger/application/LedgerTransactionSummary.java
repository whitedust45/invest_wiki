package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.LedgerSourceType;
import java.time.LocalDate;

public record LedgerTransactionSummary(String transactionId, LedgerTransactionType transactionType, LocalDate occurredOn,
                                       CurrencyCode currency, long ledgerVersion, LedgerSourceType sourceType,
                                       String importExportFileId) {
  public LedgerTransactionSummary {
    if (transactionId == null || !transactionId.matches("[0-9A-HJKMNP-TV-Z]{26}") || transactionType == null
        || occurredOn == null || ledgerVersion <= 0 || sourceType == null
        || (sourceType == LedgerSourceType.IMPORT && (importExportFileId == null || importExportFileId.isBlank()))
        || (sourceType != LedgerSourceType.IMPORT && importExportFileId != null)) {
      throw new IllegalArgumentException("transaction summary is invalid");
    }
  }

  public LedgerTransactionSummary(String transactionId, LedgerTransactionType transactionType, LocalDate occurredOn,
                                  CurrencyCode currency, long ledgerVersion) {
    this(transactionId, transactionType, occurredOn, currency, ledgerVersion, LedgerSourceType.MANUAL, null);
  }
}
