package com.personal.investment.strategy.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class StrategyReferenceNavService {
  private final StrategyReferenceNavPort referenceNavPort;
  private final StrategyIdGenerator idGenerator;

  public StrategyReferenceNavService(StrategyReferenceNavPort referenceNavPort, StrategyIdGenerator idGenerator) {
    this.referenceNavPort = referenceNavPort;
    this.idGenerator = idGenerator;
  }

  public StrategyReferenceNav record(String ownerUserId, StrategyKey strategyKey, CurrencyCode currency,
      long referenceNavCent, Instant asOfAt, Instant validUntil, String source) {
    if (ownerUserId == null || !ownerUserId.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
      throw inputInvalid("ownerUserId must be a ULID");
    }
    if (strategyKey != StrategyKey.QQQ_GROWTH && strategyKey != StrategyKey.DEEP_PUT) {
      throw currencyInvalid("reference NAV is only available for USD strategies");
    }
    if (currency != CurrencyCode.USD) {
      throw currencyInvalid("reference NAV currency must be USD");
    }
    if (referenceNavCent <= 0) {
      throw new StrategyValidationException(StrategyValidationCode.MONEY_CONVENTION_VIOLATION,
          "referenceNavCent must be a positive minor-unit integer");
    }
    if (asOfAt == null || validUntil == null || validUntil.isBefore(asOfAt)) {
      throw inputInvalid("validUntil must not be earlier than asOfAt");
    }
    if (source == null || source.isBlank() || source.length() > 32) {
      throw inputInvalid("reference NAV source is invalid");
    }
    StrategyReferenceNav value = new StrategyReferenceNav(idGenerator.next(), ownerUserId, strategyKey, currency,
        referenceNavCent, asOfAt, validUntil, source, Instant.now());
    referenceNavPort.append(value);
    return value;
  }

  private static StrategyValidationException currencyInvalid(String message) {
    return new StrategyValidationException(StrategyValidationCode.STRATEGY_CURRENCY_INVALID, message);
  }

  private static StrategyValidationException inputInvalid(String message) {
    return new StrategyValidationException(StrategyValidationCode.STRATEGY_INPUT_INVALID, message);
  }
}
