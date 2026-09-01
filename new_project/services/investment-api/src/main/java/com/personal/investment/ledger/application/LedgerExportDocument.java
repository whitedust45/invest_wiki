package com.personal.investment.ledger.application;

import java.util.List;

/**
 * Versioned JSON contract for an owner-scoped, portable ledger audit export. Numeric business values intentionally
 * remain strings so JavaScript never silently rounds a monetary minor unit.
 */
public record LedgerExportDocument(String schemaVersion, String exportId, String generatedAt,
                                   String sourceLedgerVersion, List<Account> accounts,
                                   List<Transaction> transactions) {
  public record Account(String accountId, String accountCode, String displayName, String accountKind,
                        String currency, String status, String version) { }

  public record Transaction(String transactionId, String transactionType, String occurredOn, String strategyKey,
                            String operationGroupKey, String sourceType, String importExportFileId, String correctionRootTransactionId,
                            String reversalOfTransactionId, String revisionNo, String ledgerVersion, String note,
                            List<Posting> postings, List<TradeDetail> tradeDetails, CorporateAction corporateAction,
                            Income income) { }

  public record Posting(String postingId, String accountId, String postingNo, String postingSide,
                        String amountCent, String currency) { }

  public record TradeDetail(String tradeDetailId, String detailNo, String instrumentId, String positionEffect,
                            String quantity, String unitPriceCent, String pricePoints,
                            String contractMultiplierCent, String deliveryDate, String feeCent,
                            String optionContractMultiplier) { }

  public record CorporateAction(String corporateActionId, String instrumentId, String actionType,
                                String effectiveOn, String ratioNumerator, String ratioDenominator) { }

  public record Income(String incomeDetailId, String incomeType, String instrumentId, String entitlementDate,
                       String grossAmountCent, String taxWithheldCent, String perShareAmountCent, String currency) { }
}
