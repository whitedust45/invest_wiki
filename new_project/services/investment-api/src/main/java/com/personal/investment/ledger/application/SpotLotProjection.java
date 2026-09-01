package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FifoLot;
import java.util.List;

public record SpotLotProjection(String cashAccountId, String instrumentId, CurrencyCode currency, List<FifoLot> lots) {
  public SpotLotProjection {
    lots = List.copyOf(lots);
  }
}
