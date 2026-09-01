package com.personal.investment.ledger.application;

import java.util.List;

@FunctionalInterface
public interface FuturesHistoryPort {
  List<HistoricalFuturesTrade> findAllByOwner(String ownerUserId);
}
