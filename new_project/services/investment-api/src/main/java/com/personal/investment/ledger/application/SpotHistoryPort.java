package com.personal.investment.ledger.application;

import java.util.List;

public interface SpotHistoryPort {
  List<HistoricalSpotTrade> findAllByOwner(String ownerUserId);

  /** Corporate actions are optional for compatibility with focused replay tests. */
  default List<HistoricalCorporateAction> findCorporateActionsByOwner(String ownerUserId) {
    return List.of();
  }
}
