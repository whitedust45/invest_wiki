package com.personal.investment.portfolio.application;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.portfolio.domain.ManualValuation;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualValuationService {
  private final InstrumentPort instrumentPort;
  private final ManualValuationPort valuationPort;
  private final LedgerIdGenerator idGenerator;

  public ManualValuationService(InstrumentPort instrumentPort, ManualValuationPort valuationPort,
      LedgerIdGenerator idGenerator) {
    this.instrumentPort = instrumentPort;
    this.valuationPort = valuationPort;
    this.idGenerator = idGenerator;
  }

  @Transactional
  public ManualValuation record(String ownerUserId, ManualValuationCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank() || command == null || command.instrumentId() == null
        || command.instrumentId().isBlank()) {
      throw new IllegalArgumentException("manual valuation command is invalid");
    }
    Instrument instrument = instrumentPort.findById(command.instrumentId())
        .orElseThrow(() -> new IllegalArgumentException("valuation instrument was not found"));
    if (command.currency() == null || command.currency() != instrument.nativeCurrency()) {
      throw new IllegalArgumentException("manual valuation currency must match instrument native currency");
    }
    if (instrument.assetType() == AssetType.FUTURE) {
      throw new IllegalArgumentException("futures must be valued by manual daily settlement, not manual market value");
    }
    ManualValuation valuation = new ManualValuation(idGenerator.next(), ownerUserId, instrument.instrumentId(),
        command.valuationDate(), command.currency(), command.unitPriceCent(), command.marketValueCent(),
        ManualValuation.MANUAL_PRIORITY, command.validUntil(), command.note(), ownerUserId);
    valuationPort.append(valuation);
    return valuation;
  }
}
