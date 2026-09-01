package com.personal.investment.ledger.infrastructure;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.application.SpotLotPort;
import com.personal.investment.ledger.application.SpotLotProjection;
import com.personal.investment.ledger.application.SpotProjectionRebuildPort;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FifoLot;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MyBatisSpotLotAdapter implements SpotLotPort, SpotProjectionRebuildPort {
  private static final Comparator<FifoLot> FIFO_ORDER = Comparator.comparing(FifoLot::occurredOn)
      .thenComparing(FifoLot::sourceTransactionId).thenComparingInt(FifoLot::detailNo);

  private final SpotLotMapper mapper;
  private final LedgerIdGenerator idGenerator;

  public MyBatisSpotLotAdapter(SpotLotMapper mapper, LedgerIdGenerator idGenerator) {
    this.mapper = mapper;
    this.idGenerator = idGenerator;
  }

  @Override
  public List<FifoLot> find(String ownerUserId, String cashAccountId, String instrumentId) {
    return mapper.findLots(ownerUserId, cashAccountId, instrumentId).stream()
        .map(row -> new FifoLot(row.sourceTradeDetailId(), row.sourceTransactionId(), row.detailNo(),
            row.openedOn(), row.openedQuantity(), row.remainingQuantity(), row.openedCostCent(),
            row.remainingCostCent()))
        .toList();
  }

  @Override
  public void replace(String ownerUserId, String cashAccountId, String instrumentId, CurrencyCode currency,
      long sourceLedgerVersion, List<FifoLot> updatedLots) {
    List<FifoLot> orderedLots = updatedLots.stream().sorted(FIFO_ORDER).toList();
    BigDecimal remainingQuantity = orderedLots.stream().map(FifoLot::remainingQuantity)
        .reduce(BigDecimal.ZERO.setScale(8), BigDecimal::add);
    String positionId = mapper.findPositionId(ownerUserId, cashAccountId, instrumentId);
    if (positionId == null) {
      positionId = idGenerator.next();
      mapper.insertPosition(new SpotLotMapper.PositionRow(positionId, ownerUserId, cashAccountId, instrumentId,
          remainingQuantity, currency.name(), sourceLedgerVersion));
    } else if (mapper.updatePosition(new SpotLotMapper.PositionUpdateRow(positionId, remainingQuantity,
        currency.name(), sourceLedgerVersion)) != 1) {
      throw new IllegalStateException("spot position projection was not updated");
    }
    Map<String, String> lotIds = new HashMap<>();
    for (SpotLotMapper.LotIdRow row : mapper.findLotIds(positionId)) {
      lotIds.put(row.sourceTradeDetailId(), row.positionLotId());
    }
    mapper.deleteLots(positionId);
    int lotNo = 1;
    for (FifoLot lot : orderedLots) {
      String positionLotId = lotIds.getOrDefault(lot.sourceTradeDetailId(), idGenerator.next());
      mapper.insertLot(new SpotLotMapper.LotInsertRow(positionLotId, positionId, lot.sourceTradeDetailId(), lotNo++,
          lot.occurredOn(), lot.openedQuantity(), lot.remainingQuantity(), currency.name(), sourceLedgerVersion,
          lot.openedCostCent(), lot.remainingCostCent()));
    }
  }

  @Override
  public void replaceOwnerProjection(String ownerUserId, long sourceLedgerVersion,
      List<SpotLotProjection> projections) {
    mapper.deleteLotsByOwner(ownerUserId);
    mapper.deletePositionsByOwner(ownerUserId);
    for (SpotLotProjection projection : projections) {
      if (!projection.lots().isEmpty()) {
        replace(ownerUserId, projection.cashAccountId(), projection.instrumentId(), projection.currency(),
            sourceLedgerVersion, projection.lots());
      }
    }
  }
}
