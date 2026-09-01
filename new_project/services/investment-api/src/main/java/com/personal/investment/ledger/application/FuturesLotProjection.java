package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FuturesLot;
import java.util.List;

public record FuturesLotProjection(String cashAccountId, String lockedMarginAccountId, String instrumentId,
                                   CurrencyCode currency, List<FuturesLot> lots) {
  public FuturesLotProjection {
    lots = List.copyOf(lots);
  }
}
