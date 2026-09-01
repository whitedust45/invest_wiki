package com.personal.investment.platform.application;

/** Fully normalized command values. Money fields are minor-unit decimal strings; no JSON number is used for money. */
public record LegacyImportPreviewLine(int sourceRow, String status, String code, LegacyImportOperation operation,
    String occurredOn, String cashAccountId, String instrumentId, String quantity, String unitPriceCent,
    String pricePoints, String amountCent, String initialMarginCent, String feeCent, String entitlementDate,
    String note) {
}
