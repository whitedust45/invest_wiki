package com.personal.investment.market.infrastructure;

import com.personal.investment.ledger.application.SpotInstrumentPort;
import com.personal.investment.ledger.domain.TradableInstrument;
import com.personal.investment.ledger.domain.TradableInstrumentType;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.market.domain.AssetType;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Market read adapter for Ledger. It deliberately exposes no Market write operation. */
@Component
public class MyBatisSpotInstrumentAdapter implements SpotInstrumentPort {
  private final InstrumentPort instrumentPort;

  public MyBatisSpotInstrumentAdapter(InstrumentPort instrumentPort) {
    this.instrumentPort = instrumentPort;
  }

  @Override
  public Optional<TradableInstrument> findById(String instrumentId) {
    return instrumentPort.findById(instrumentId).flatMap(instrument -> switch (instrument.assetType()) {
      case EQUITY -> Optional.of(new TradableInstrument(instrument.instrumentId(), TradableInstrumentType.EQUITY,
          instrument.nativeCurrency()));
      case ETF -> Optional.of(new TradableInstrument(instrument.instrumentId(), TradableInstrumentType.ETF,
          instrument.nativeCurrency()));
      case INDEX, FUTURE, OPTION -> Optional.empty();
    });
  }
}
