package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FuturesLot;
import java.util.List;

public interface FuturesPositionPort {
  List<FuturesLot> find(String ownerUserId, String lockedMarginAccountId, String instrumentId);

  void replace(String ownerUserId, String lockedMarginAccountId, String instrumentId, CurrencyCode currency,
               long sourceLedgerVersion, List<FuturesLot> lots);
}
