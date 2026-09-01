package com.personal.investment.market.infrastructure;

import com.personal.investment.ledger.application.FuturesInstrument;
import com.personal.investment.ledger.application.FuturesInstrumentPort;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.InstrumentStatus;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Read-only Market-to-Ledger boundary for immutable CFFEX futures metadata. */
@Component
public class MyBatisFuturesInstrumentAdapter implements FuturesInstrumentPort {
  private final InstrumentPort instrumentPort;

  public MyBatisFuturesInstrumentAdapter(InstrumentPort instrumentPort) {
    this.instrumentPort = instrumentPort;
  }

  @Override
  public Optional<FuturesInstrument> findById(String instrumentId) {
    return instrumentPort.findById(instrumentId).filter(instrument -> instrument.assetType() == AssetType.FUTURE
        && instrument.status() == InstrumentStatus.ACTIVE && instrument.futureSpecification() != null)
        .map(instrument -> new FuturesInstrument(instrument.instrumentId(), instrument.nativeCurrency(),
            instrument.maturityDate(), instrument.futureSpecification().contractMultiplierCent()));
  }
}
