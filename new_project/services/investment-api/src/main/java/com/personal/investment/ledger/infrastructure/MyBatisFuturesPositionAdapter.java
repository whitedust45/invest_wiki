package com.personal.investment.ledger.infrastructure;

import com.personal.investment.ledger.application.FuturesPositionPort;
import com.personal.investment.ledger.application.FuturesLotProjection;
import com.personal.investment.ledger.application.FuturesProjectionRebuildPort;
import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FuturesLot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MyBatisFuturesPositionAdapter implements FuturesPositionPort, FuturesProjectionRebuildPort {
  private final FuturesPositionMapper mapper;
  private final LedgerIdGenerator idGenerator;

  public MyBatisFuturesPositionAdapter(FuturesPositionMapper mapper, LedgerIdGenerator idGenerator) {
    this.mapper = mapper;
    this.idGenerator = idGenerator;
  }

  @Override
  public List<FuturesLot> find(String ownerUserId, String lockedMarginAccountId, String instrumentId) {
    return mapper.findLots(ownerUserId, lockedMarginAccountId, instrumentId).stream().map(row -> new FuturesLot(
        row.sourceTradeDetailId(), row.openedOn(), row.openedQuantity(), row.remainingQuantity(),
        row.openPricePoints(), row.lastSettlementPricePoints(), row.lastSettlementOn(), row.contractMultiplierCent(),
        row.allocatedInitialMarginCent(), row.remainingInitialMarginCent(), CurrencyCode.of(row.currency()))).toList();
  }

  @Override
  public void replace(String ownerUserId, String lockedMarginAccountId, String instrumentId, CurrencyCode currency,
      long sourceLedgerVersion, List<FuturesLot> lots) {
    mapper.deleteLots(ownerUserId, lockedMarginAccountId, instrumentId);
    mapper.deletePosition(ownerUserId, lockedMarginAccountId, instrumentId);
    persist(ownerUserId, lockedMarginAccountId, instrumentId, currency, sourceLedgerVersion, lots);
  }

  @Override
  public void replaceOwnerProjection(String ownerUserId, long sourceLedgerVersion, List<FuturesLotProjection> projections) {
    mapper.deleteOwnerLots(ownerUserId);
    mapper.deleteOwnerPositions(ownerUserId);
    for (FuturesLotProjection projection : projections) {
      persist(ownerUserId, projection.lockedMarginAccountId(), projection.instrumentId(), projection.currency(),
          sourceLedgerVersion, projection.lots());
    }
  }

  private void persist(String ownerUserId, String lockedMarginAccountId, String instrumentId, CurrencyCode currency,
      long sourceLedgerVersion, List<FuturesLot> lots) {
    List<FuturesLot> openLots = lots.stream().filter(lot -> lot.remainingQuantity().signum() > 0).toList();
    if (openLots.isEmpty()) {
      return;
    }
    String positionId = idGenerator.next();
    BigDecimal quantity = openLots.stream().map(FuturesLot::remainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal weightedPoints = openLots.stream().map(lot -> lot.remainingQuantity().multiply(lot.openPricePoints()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal averagePoints = weightedPoints.divide(quantity, 8, RoundingMode.HALF_UP);
    long multiplier = openLots.getFirst().contractMultiplierCent();
    if (openLots.stream().anyMatch(lot -> lot.contractMultiplierCent() != multiplier || lot.currency() != currency)) {
      throw new IllegalStateException("futures lots have inconsistent contract snapshots");
    }
    if (mapper.insertPosition(new FuturesPositionMapper.PositionRow(positionId, ownerUserId, lockedMarginAccountId,
        instrumentId, quantity, averagePoints, multiplier, currency.name(), sourceLedgerVersion, sourceLedgerVersion)) != 1) {
      throw new IllegalStateException("futures position was not inserted");
    }
    for (FuturesLot lot : openLots) {
      if (mapper.insertLot(new FuturesPositionMapper.LotInsertRow(idGenerator.next(), positionId,
          lot.sourceTradeDetailId(), lot.openedOn(), lot.openedQuantity(), lot.remainingQuantity(),
          lot.openPricePoints(), lot.lastSettlementPricePoints(), lot.lastSettlementOn(), lot.contractMultiplierCent(),
          lot.allocatedInitialMarginCent(), lot.remainingInitialMarginCent(), lot.currency().name(),
          sourceLedgerVersion)) != 1) {
        throw new IllegalStateException("futures position lot was not inserted");
      }
    }
  }
}
