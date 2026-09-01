package com.personal.investment.market.infrastructure;

import com.personal.investment.ledger.application.OptionInstrument;
import com.personal.investment.ledger.application.OptionInstrumentPort;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.InstrumentStatus;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Read-only Market-to-Ledger boundary for complete active long-option contract metadata. */
@Component
public class MyBatisOptionInstrumentAdapter implements OptionInstrumentPort {
  private final InstrumentPort instrumentPort;

  public MyBatisOptionInstrumentAdapter(InstrumentPort instrumentPort) {
    this.instrumentPort = instrumentPort;
  }

  @Override
  public Optional<OptionInstrument> findById(String instrumentId) {
    return instrumentPort.findById(instrumentId).filter(instrument -> instrument.assetType() == AssetType.OPTION
        && instrument.status() == InstrumentStatus.ACTIVE && instrument.optionSpecification() != null)
        .map(instrument -> new OptionInstrument(instrument.instrumentId(), instrument.nativeCurrency(),
            instrument.maturityDate(), instrument.optionSpecification().contractMultiplier()));
  }
}
