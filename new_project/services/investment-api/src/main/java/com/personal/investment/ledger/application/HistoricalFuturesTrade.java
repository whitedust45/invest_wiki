package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.Quantity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable future detail plus the derived margin-account ownership needed for a deterministic projection replay. */
public record HistoricalFuturesTrade(String transactionId, LedgerTransactionType transactionType, LocalDate occurredOn,
                                     String cashAccountId, String lockedMarginAccountId, String instrumentId,
                                     String tradeDetailId, int detailNo, BigDecimal quantity, BigDecimal pricePoints,
                                     long contractMultiplierCent, long initialMarginCent, CurrencyCode currency) {
  public HistoricalFuturesTrade {
    requireText(transactionId, "transactionId");
    if (transactionType != LedgerTransactionType.FUTURES_OPEN && transactionType != LedgerTransactionType.FUTURES_CLOSE
        && transactionType != LedgerTransactionType.FUTURES_DAILY_SETTLEMENT) {
      throw new IllegalArgumentException("historical futures trade type is unsupported");
    }
    Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    requireText(cashAccountId, "cashAccountId");
    requireText(lockedMarginAccountId, "lockedMarginAccountId");
    requireText(instrumentId, "instrumentId");
    requireText(tradeDetailId, "tradeDetailId");
    if (detailNo < 1 || contractMultiplierCent <= 0 || initialMarginCent < 0) {
      throw new IllegalArgumentException("historical futures numeric snapshot is invalid");
    }
    Quantity.of(quantity);
    Quantity.of(pricePoints);
    Objects.requireNonNull(currency, "currency must not be null");
    if (currency != CurrencyCode.CNY) {
      throw new IllegalArgumentException("historical futures must use CNY");
    }
    if (transactionType == LedgerTransactionType.FUTURES_OPEN && initialMarginCent <= 0) {
      throw new IllegalArgumentException("historical futures open requires initial margin");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
