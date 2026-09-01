package com.personal.investment.market.application;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.market.domain.InstrumentConflictException;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstrumentService {
  private final InstrumentPort instrumentPort;
  private final LedgerIdGenerator idGenerator;

  public InstrumentService(InstrumentPort instrumentPort, LedgerIdGenerator idGenerator) {
    this.instrumentPort = instrumentPort;
    this.idGenerator = idGenerator;
  }

  @Transactional
  public Instrument create(CreateInstrumentCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    Instrument requested = Instrument.newActive(idGenerator.next(), command.market(), command.exchange(),
        command.symbol(), command.displayName(), command.assetType(), command.nativeCurrency(),
        command.maturityDate(), command.futureSpecification(), command.optionSpecification(), command.tushareCode(),
        command.underlyingInstrumentId());
    var existing = instrumentPort.findByNaturalKey(requested.market(), requested.exchange(), requested.symbol());
    if (existing.isPresent()) {
      if (existing.get().hasSameDefinition(requested)) {
        return existing.get();
      }
      throw new InstrumentConflictException(requested.market(), requested.exchange(), requested.symbol());
    }
    validateOptionUnderlying(requested);
    validateFutureUnderlying(requested);
    String futureContractId = requested.assetType() == AssetType.FUTURE ? idGenerator.next() : null;
    String optionContractId = requested.assetType() == AssetType.OPTION ? idGenerator.next() : null;
    try {
      instrumentPort.insert(requested, futureContractId, optionContractId);
    } catch (DuplicateKeyException duplicate) {
      Instrument concurrent = instrumentPort.findByNaturalKey(requested.market(), requested.exchange(),
              requested.symbol())
          .orElseThrow(() -> duplicate);
      if (concurrent.hasSameDefinition(requested)) {
        return concurrent;
      }
      throw new InstrumentConflictException(requested.market(), requested.exchange(), requested.symbol());
    }
    return requested;
  }

  @Transactional(readOnly = true)
  public List<Instrument> list() {
    return instrumentPort.findAll();
  }

  @Transactional
  public Instrument updateTushareCode(String instrumentId, String tushareCode) {
    Instrument existing = instrumentPort.findById(instrumentId)
        .orElseThrow(() -> new IllegalArgumentException("instrument was not found"));
    Instrument updated = new Instrument(existing.instrumentId(), existing.market(), existing.exchange(),
        existing.symbol(), existing.displayName(), existing.assetType(), existing.nativeCurrency(),
        existing.maturityDate(), existing.status(), existing.futureSpecification(), existing.optionSpecification(),
        tushareCode, existing.underlyingInstrumentId());
    instrumentPort.updateTushareCode(updated.instrumentId(), updated.tushareCode());
    return updated;
  }

  private void validateFutureUnderlying(Instrument requested) {
    if (requested.assetType() != AssetType.FUTURE) {
      return;
    }
    if (requested.underlyingInstrumentId() == null) {
      throw new IllegalArgumentException("future underlying must be a CNY index instrument");
    }
    Instrument underlying = instrumentPort.findById(requested.underlyingInstrumentId())
        .orElseThrow(() -> new IllegalArgumentException("future underlying index was not found"));
    if (underlying.assetType() != AssetType.INDEX || underlying.nativeCurrency() != CurrencyCode.CNY) {
      throw new IllegalArgumentException("future underlying must be a CNY index instrument");
    }
  }

  private void validateOptionUnderlying(Instrument requested) {
    if (requested.assetType() != AssetType.OPTION) {
      return;
    }
    Instrument underlying = instrumentPort.findById(requested.optionSpecification().underlyingInstrumentId())
        .orElseThrow(() -> new IllegalArgumentException("option underlying instrument was not found"));
    if ((underlying.assetType() != AssetType.EQUITY && underlying.assetType() != AssetType.ETF)
        || underlying.nativeCurrency() != requested.nativeCurrency()) {
      throw new IllegalArgumentException("option underlying must be a same-currency equity or ETF");
    }
  }
}
