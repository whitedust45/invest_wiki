package com.personal.investment.market.application;

import com.personal.investment.market.domain.Instrument;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Infrastructure boundary for the single approved automatic provider. It must never be called by an HTTP thread. */
public interface MarketAutomaticSource {
  MarketAutomaticSourceResult refresh(List<Instrument> instruments, LocalDate tradingDate);

  record MarketAutomaticSourceResult(List<Quote> quotes, List<Metric> metrics, List<Issue> issues) {
    public MarketAutomaticSourceResult {
      quotes = List.copyOf(quotes);
      metrics = List.copyOf(metrics);
      issues = List.copyOf(issues);
    }
  }

  record Quote(String instrumentId, Instant quoteTime, String sourceObservationKey, long priceCent,
               Long prevCloseCent) {
  }

  record Metric(String instrumentId, LocalDate tradeDate, String metricName, BigDecimal valueDecimal,
                String sourceObservationKey) {
  }

  record Issue(String instrumentId, String errorCode, String summary) {
  }
}
