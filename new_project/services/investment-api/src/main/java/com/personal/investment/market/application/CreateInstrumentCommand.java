package com.personal.investment.market.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.FutureSpecification;
import com.personal.investment.market.domain.OptionSpecification;
import java.time.LocalDate;

public record CreateInstrumentCommand(
    String market,
    String exchange,
    String symbol,
    String displayName,
    AssetType assetType,
    CurrencyCode nativeCurrency,
    LocalDate maturityDate,
    FutureSpecification futureSpecification,
    OptionSpecification optionSpecification,
    String tushareCode,
    String underlyingInstrumentId) {

  public CreateInstrumentCommand(String market, String exchange, String symbol, String displayName,
      AssetType assetType, CurrencyCode nativeCurrency, LocalDate maturityDate,
      FutureSpecification futureSpecification, OptionSpecification optionSpecification) {
    this(market, exchange, symbol, displayName, assetType, nativeCurrency, maturityDate, futureSpecification,
        optionSpecification, null, null);
  }
}
