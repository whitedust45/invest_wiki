package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerSourceType;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.PositionEffect;
import com.personal.investment.ledger.domain.PostingSide;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Owner-isolated immutable ledger detail used for audit and correction entry points. */
public record LedgerTransactionDetail(String transactionId, LedgerTransactionType transactionType, LocalDate occurredOn,
                                      String strategyKey, String operationGroupKey, LedgerSourceType sourceType, String importExportFileId,
                                      String correctionRootTransactionId, String reversalOfTransactionId, int revisionNo,
                                      long ledgerVersion, String note, boolean correctable, List<Posting> postings,
                                      List<TradeDetail> tradeDetails, CorporateAction corporateAction, Income income) {
  public LedgerTransactionDetail {
    requireUlid(transactionId, "transactionId");
    Objects.requireNonNull(transactionType, "transactionType must not be null");
    Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    Objects.requireNonNull(sourceType, "sourceType must not be null");
    if (operationGroupKey != null) {
      requireUlid(operationGroupKey, "operationGroupKey");
    }
    requireUlid(correctionRootTransactionId, "correctionRootTransactionId");
    if (reversalOfTransactionId != null) {
      requireUlid(reversalOfTransactionId, "reversalOfTransactionId");
    }
    if (revisionNo < 0 || ledgerVersion < 1) {
      throw new IllegalArgumentException("ledger detail revision or version is invalid");
    }
    postings = List.copyOf(postings);
    tradeDetails = List.copyOf(tradeDetails);
  }

  public record Posting(String postingId, String accountId, int postingNo, PostingSide postingSide, long amountCent,
                        CurrencyCode currency) {
    public Posting {
      requireUlid(postingId, "postingId");
      requireUlid(accountId, "accountId");
      if (postingNo < 1 || amountCent < 1) {
        throw new IllegalArgumentException("posting sequence or amount is invalid");
      }
      Objects.requireNonNull(postingSide, "postingSide must not be null");
      Objects.requireNonNull(currency, "currency must not be null");
    }
  }

  public record TradeDetail(String tradeDetailId, int detailNo, String instrumentId, PositionEffect positionEffect,
                            BigDecimal quantity, Long unitPriceCent, BigDecimal pricePoints,
                            Long contractMultiplierCent, LocalDate deliveryDate, long feeCent,
                            Long optionContractMultiplier) {
    public TradeDetail {
      requireUlid(tradeDetailId, "tradeDetailId");
      requireUlid(instrumentId, "instrumentId");
      if (detailNo < 1 || quantity == null || quantity.signum() <= 0 || feeCent < 0) {
        throw new IllegalArgumentException("trade detail is invalid");
      }
      Objects.requireNonNull(positionEffect, "positionEffect must not be null");
    }
  }

  public record CorporateAction(String corporateActionId, String instrumentId, String actionType,
                                LocalDate effectiveOn, long ratioNumerator, long ratioDenominator) {
    public CorporateAction {
      requireUlid(corporateActionId, "corporateActionId");
      requireUlid(instrumentId, "instrumentId");
      if (actionType == null || actionType.isBlank() || effectiveOn == null || ratioNumerator < 1 || ratioDenominator < 1) {
        throw new IllegalArgumentException("corporate action detail is invalid");
      }
    }
  }

  public record Income(String incomeDetailId, String incomeType, String instrumentId, LocalDate entitlementDate,
                       long grossAmountCent, long taxWithheldCent, Long perShareAmountCent, CurrencyCode currency) {
    public Income {
      requireUlid(incomeDetailId, "incomeDetailId");
      if ((!"DIVIDEND".equals(incomeType) && !"INTEREST".equals(incomeType))
          || grossAmountCent < 1 || taxWithheldCent < 0 || taxWithheldCent > grossAmountCent || currency == null
          || ("DIVIDEND".equals(incomeType) && (instrumentId == null || entitlementDate == null))
          || (perShareAmountCent != null && perShareAmountCent < 1)) {
        throw new IllegalArgumentException("income detail is invalid");
      }
      if (instrumentId != null) {
        requireUlid(instrumentId, "income instrumentId");
      }
    }
  }

  private static void requireUlid(String value, String field) {
    if (value == null || !value.matches("^[0-9A-HJKMNP-TV-Z]{26}$")) {
      throw new IllegalArgumentException(field + " must be a ULID");
    }
  }
}
