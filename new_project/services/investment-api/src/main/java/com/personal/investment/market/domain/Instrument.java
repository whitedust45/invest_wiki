package com.personal.investment.market.domain;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/** Immutable Market aggregate. Ledger references only its business identifier. */
public record Instrument(
    String instrumentId,
    String market,
    String exchange,
    String symbol,
    String displayName,
    AssetType assetType,
    CurrencyCode nativeCurrency,
    LocalDate maturityDate,
    InstrumentStatus status,
    FutureSpecification futureSpecification,
    OptionSpecification optionSpecification,
    String tushareCode,
    String underlyingInstrumentId) {

  public Instrument {
    requireText(instrumentId, "instrumentId");
    market = normalizeCode(market, "market", 32);
    exchange = normalizeCode(exchange, "exchange", 32);
    symbol = normalizeCode(symbol, "symbol", 64);
    displayName = normalizeDisplayName(displayName);
    Objects.requireNonNull(assetType, "assetType must not be null");
    Objects.requireNonNull(nativeCurrency, "nativeCurrency must not be null");
    Objects.requireNonNull(status, "status must not be null");
    tushareCode = normalizeOptionalCode(tushareCode, "tushareCode", 64);
    underlyingInstrumentId = normalizeOptionalUlid(underlyingInstrumentId, "underlyingInstrumentId");
    switch (assetType) {
      case EQUITY, ETF, INDEX -> requireNoDerivativeFields(maturityDate, futureSpecification, optionSpecification,
          underlyingInstrumentId);
      case FUTURE -> validateFuture(market, exchange, nativeCurrency, maturityDate, futureSpecification,
          optionSpecification, underlyingInstrumentId);
      case OPTION -> validateOption(maturityDate, futureSpecification, optionSpecification, underlyingInstrumentId);
    }
  }

  /** Compatibility constructor for data that has no external source mapping or basis anchor. */
  public Instrument(String instrumentId, String market, String exchange, String symbol, String displayName,
      AssetType assetType, CurrencyCode nativeCurrency, LocalDate maturityDate, InstrumentStatus status,
      FutureSpecification futureSpecification, OptionSpecification optionSpecification) {
    this(instrumentId, market, exchange, symbol, displayName, assetType, nativeCurrency, maturityDate, status,
        futureSpecification, optionSpecification, null, null);
  }

  public static Instrument newActive(String instrumentId, String market, String exchange, String symbol,
      String displayName, AssetType assetType, CurrencyCode nativeCurrency, LocalDate maturityDate,
      FutureSpecification futureSpecification, OptionSpecification optionSpecification) {
    return new Instrument(instrumentId, market, exchange, symbol, displayName, assetType, nativeCurrency,
        maturityDate, InstrumentStatus.ACTIVE, futureSpecification, optionSpecification, null, null);
  }

  public static Instrument newActive(String instrumentId, String market, String exchange, String symbol,
      String displayName, AssetType assetType, CurrencyCode nativeCurrency, LocalDate maturityDate,
      FutureSpecification futureSpecification, OptionSpecification optionSpecification, String tushareCode,
      String underlyingInstrumentId) {
    return new Instrument(instrumentId, market, exchange, symbol, displayName, assetType, nativeCurrency,
        maturityDate, InstrumentStatus.ACTIVE, futureSpecification, optionSpecification, tushareCode,
        underlyingInstrumentId);
  }

  public boolean hasSameDefinition(Instrument other) {
    return other != null
        && market.equals(other.market)
        && exchange.equals(other.exchange)
        && symbol.equals(other.symbol)
        && displayName.equals(other.displayName)
        && assetType == other.assetType
        && nativeCurrency == other.nativeCurrency
        && Objects.equals(maturityDate, other.maturityDate)
        && Objects.equals(futureSpecification, other.futureSpecification)
        && Objects.equals(optionSpecification, other.optionSpecification)
        && Objects.equals(tushareCode, other.tushareCode)
        && Objects.equals(underlyingInstrumentId, other.underlyingInstrumentId);
  }

  private static void validateFuture(String market, String exchange, CurrencyCode currency, LocalDate maturityDate,
      FutureSpecification futureSpecification, OptionSpecification optionSpecification, String underlyingInstrumentId) {
    if (!"CFFEX".equals(market) || !"CFFEX".equals(exchange) || currency != CurrencyCode.CNY) {
      throw new IllegalArgumentException("future must be a CFFEX CNY contract");
    }
    if (maturityDate == null || futureSpecification == null || optionSpecification != null) {
      throw new IllegalArgumentException("future requires maturityDate and future specification");
    }
  }

  private static void validateOption(LocalDate maturityDate, FutureSpecification futureSpecification,
      OptionSpecification optionSpecification, String underlyingInstrumentId) {
    if (maturityDate == null || futureSpecification != null || optionSpecification == null || underlyingInstrumentId != null) {
      throw new IllegalArgumentException("option requires maturityDate and option only");
    }
  }

  private static void requireNoDerivativeFields(LocalDate maturityDate, FutureSpecification futureSpecification,
      OptionSpecification optionSpecification, String underlyingInstrumentId) {
    if (maturityDate != null || futureSpecification != null || optionSpecification != null || underlyingInstrumentId != null) {
      throw new IllegalArgumentException("spot and index instruments must not contain derivative fields");
    }
  }

  private static String normalizeCode(String value, String field, int maxLength) {
    requireText(value, field);
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
    }
    return normalized;
  }

  private static String normalizeDisplayName(String value) {
    requireText(value, "displayName");
    String normalized = value.trim();
    if (normalized.length() > 256) {
      throw new IllegalArgumentException("displayName exceeds 256 characters");
    }
    return normalized;
  }

  private static String normalizeOptionalCode(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return normalizeCode(value, field, maxLength);
  }

  private static String normalizeOptionalUlid(String value, String field) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    if (!normalized.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
      throw new IllegalArgumentException(field + " must be a ULID");
    }
    return normalized;
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
