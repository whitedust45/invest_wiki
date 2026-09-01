package com.personal.investment.market.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Trusted import payload. Monetary values are minor-unit integers before this command is constructed. */
public record MarketSnapshotSubmissionCommand(
    LocalDate tradingDate,
    String sourceName,
    String sourceReference,
    List<Quote> quotes,
    List<Metric> metrics,
    List<Basis> basis) {

  public record Quote(String instrumentId, Instant quoteTime, String sourceObservationKey, long priceCent,
                      Long prevCloseCent, CurrencyCode currency) {
  }

  public record Metric(String instrumentId, String metricName, BigDecimal metricValueDecimal,
                       String sourceObservationKey) {
  }

  public record Basis(String underlyingInstrumentId, String futureInstrumentId, BigDecimal spotPricePoints,
                      BigDecimal futurePricePoints, BigDecimal annualizedBasisDecimal, LocalDate maturityDate,
                      Integer daysLeft, String sourceObservationKey) {
  }
}
