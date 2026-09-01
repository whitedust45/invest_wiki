package com.personal.investment.portfolio.application;

import java.time.LocalDate;
import java.util.List;

public record PortfolioReconciliationCommand(String cashAccountId, LocalDate reconciliationDate, long brokerCashCent,
                                             List<BrokerPosition> positions,
                                             String attachmentImportExportFileId, String discrepancyReason) {
}
