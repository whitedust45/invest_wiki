package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.FifoLot;
import com.personal.investment.ledger.domain.CurrencyCode;
import java.util.List;

/** Mutable replay projection boundary. It never mutates ledger facts. */
public interface SpotLotPort {
  List<FifoLot> find(String ownerUserId, String cashAccountId, String instrumentId);

  void replace(String ownerUserId, String cashAccountId, String instrumentId, CurrencyCode currency,
      long sourceLedgerVersion, List<FifoLot> updatedLots);
}
