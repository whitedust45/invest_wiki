package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalSpotTrade(String transactionId, LedgerTransactionType transactionType, LocalDate occurredOn,
                                  String cashAccountId, String instrumentId, String tradeDetailId, int detailNo,
                                  BigDecimal quantity, Long unitPriceCent, long feeCent,
                                  Long optionContractMultiplier, CurrencyCode currency) {
  public HistoricalSpotTrade {
    if (feeCent < 0) {
      throw new IllegalArgumentException("historical FIFO trade fee must not be negative");
    }
    switch (transactionType) {
      case TRADE_BUY, TRADE_SELL -> {
        if (unitPriceCent == null || unitPriceCent <= 0 || optionContractMultiplier != null) {
          throw new IllegalArgumentException("historical spot trade fields are invalid");
        }
      }
      case OPTION_OPEN, OPTION_CLOSE -> {
        if (unitPriceCent == null || unitPriceCent <= 0 || optionContractMultiplier == null
            || optionContractMultiplier <= 0) {
          throw new IllegalArgumentException("historical option trade fields are invalid");
        }
      }
      case OPTION_EXPIRE -> {
        if (unitPriceCent != null || optionContractMultiplier == null || optionContractMultiplier <= 0 || feeCent != 0) {
          throw new IllegalArgumentException("historical option expiry fields are invalid");
        }
      }
      default -> throw new IllegalArgumentException("historical FIFO trade type is invalid");
    }
  }

  public boolean isOpeningTrade() {
    return transactionType == LedgerTransactionType.TRADE_BUY || transactionType == LedgerTransactionType.OPTION_OPEN;
  }

  public boolean isOption() {
    return transactionType == LedgerTransactionType.OPTION_OPEN || transactionType == LedgerTransactionType.OPTION_CLOSE
        || transactionType == LedgerTransactionType.OPTION_EXPIRE;
  }
}
