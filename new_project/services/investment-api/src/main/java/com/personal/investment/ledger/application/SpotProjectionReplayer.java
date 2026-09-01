package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.FifoAllocation;
import com.personal.investment.ledger.domain.FifoCostAllocator;
import com.personal.investment.ledger.domain.FifoLot;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.Quantity;
import com.personal.investment.ledger.domain.SpotTradeMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rebuilds stock/ETF and long-option FIFO lots solely from immutable trade and corporate-action facts. */
@Service
public class SpotProjectionReplayer implements SpotHistoryReplayer {
  private static final Comparator<ReplayEvent> ORDER = Comparator.comparing(ReplayEvent::occurredOn)
      .thenComparing(ReplayEvent::transactionId).thenComparingInt(ReplayEvent::detailNo);

  private final SpotHistoryPort historyPort;
  private final SpotProjectionRebuildPort projectionPort;

  public SpotProjectionReplayer(SpotHistoryPort historyPort, SpotProjectionRebuildPort projectionPort) {
    this.historyPort = historyPort;
    this.projectionPort = projectionPort;
  }

  @Override
  @Transactional
  public void rebuild(String ownerUserId, long sourceLedgerVersion) {
    projectionPort.replaceOwnerProjection(ownerUserId, sourceLedgerVersion, calculate(ownerUserId, null));
  }

  @Override
  public void validateCorporateAction(String ownerUserId, HistoricalCorporateAction action) {
    calculate(ownerUserId, action);
  }

  @Override
  public BigDecimal quantityAt(String ownerUserId, String instrumentId, java.time.LocalDate asOf) {
    if (instrumentId == null || instrumentId.isBlank() || asOf == null) {
      throw new IllegalArgumentException("instrumentId and asOf are required for historical quantity replay");
    }
    return calculate(ownerUserId, null, asOf).stream().filter(projection -> projection.instrumentId().equals(instrumentId))
        .flatMap(projection -> projection.lots().stream()).map(FifoLot::remainingQuantity)
        .reduce(BigDecimal.ZERO.setScale(8), BigDecimal::add);
  }

  @Override
  public List<HistoricalFifoPosition> positionsAt(String ownerUserId, String cashAccountId,
      java.time.LocalDate asOf) {
    if (cashAccountId == null || cashAccountId.isBlank() || asOf == null) {
      throw new IllegalArgumentException("cashAccountId and asOf are required for historical FIFO position replay");
    }
    return calculate(ownerUserId, null, asOf).stream().filter(projection -> projection.cashAccountId().equals(cashAccountId))
        // A fully closed lot is a valid replay result but not a valid HistoricalFifoPosition (which is positive-only).
        .filter(projection -> remainingQuantity(projection).signum() > 0)
        .map(projection -> new HistoricalFifoPosition(projection.cashAccountId(), projection.instrumentId(),
            projection.currency(), remainingQuantity(projection), remainingCost(projection))).toList();
  }

  private static BigDecimal remainingQuantity(SpotLotProjection projection) {
    return projection.lots().stream().map(FifoLot::remainingQuantity)
        .reduce(BigDecimal.ZERO.setScale(8), BigDecimal::add);
  }

  private static long remainingCost(SpotLotProjection projection) {
    return projection.lots().stream().mapToLong(FifoLot::remainingCostCent).reduce(0L, Math::addExact);
  }

  private List<SpotLotProjection> calculate(String ownerUserId, HistoricalCorporateAction proposedAction) {
    return calculate(ownerUserId, proposedAction, null);
  }

  private List<SpotLotProjection> calculate(String ownerUserId, HistoricalCorporateAction proposedAction,
      java.time.LocalDate asOf) {
    try {
      Map<ProjectionKey, List<FifoLot>> lotsByKey = new LinkedHashMap<>();
      List<ReplayEvent> events = new ArrayList<>();
      historyPort.findAllByOwner(ownerUserId).forEach(trade -> events.add(ReplayEvent.trade(trade)));
      historyPort.findCorporateActionsByOwner(ownerUserId).forEach(action -> events.add(ReplayEvent.action(action)));
      if (proposedAction != null) {
        events.add(ReplayEvent.action(proposedAction));
      }
      for (ReplayEvent event : events.stream().filter(event -> asOf == null || !event.occurredOn().isAfter(asOf))
          .sorted(ORDER).toList()) {
        if (event.trade() != null) {
          applyTrade(lotsByKey, event.trade());
        } else {
          applyCorporateAction(lotsByKey, event.action());
        }
      }
      return lotsByKey.entrySet().stream().map(entry ->
          new SpotLotProjection(entry.getKey().cashAccountId(), entry.getKey().instrumentId(),
              entry.getKey().currency(), entry.getValue())).toList();
    } catch (CorporateActionNoOpenPositionException | CorporateActionRatioException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new ReplayInvariantViolationException("historical spot replay violates FIFO or cost invariants", exception);
    }
  }

  private static void applyTrade(Map<ProjectionKey, List<FifoLot>> lotsByKey, HistoricalSpotTrade trade) {
    ProjectionKey key = new ProjectionKey(trade.cashAccountId(), trade.instrumentId(), trade.currency());
    List<FifoLot> currentLots = lotsByKey.computeIfAbsent(key, ignored -> new ArrayList<>());
    if (trade.isOpeningTrade()) {
      long grossCostCent = trade.isOption() ? optionGrossCostCent(trade.quantity(), trade.optionContractMultiplier(),
          trade.unitPriceCent()) : SpotTradeMath.grossCostCent(trade.quantity(), trade.unitPriceCent());
      long totalCostCent = Math.addExact(grossCostCent, trade.feeCent());
      currentLots.add(new FifoLot(trade.tradeDetailId(), trade.transactionId(), trade.detailNo(), trade.occurredOn(),
          Quantity.of(trade.quantity()), Quantity.of(trade.quantity()), totalCostCent, totalCostCent));
      return;
    }
    FifoAllocation allocation = FifoCostAllocator.allocate(currentLots, trade.quantity());
    currentLots.clear();
    currentLots.addAll(allocation.remainingLots());
  }

  private static long optionGrossCostCent(BigDecimal quantity, long contractMultiplier, long unitPriceCent) {
    try {
      return Quantity.of(quantity).multiply(BigDecimal.valueOf(contractMultiplier)).multiply(BigDecimal.valueOf(unitPriceCent))
          .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    } catch (ArithmeticException exception) {
      throw new ReplayInvariantViolationException("historical option premium is not representable in whole minor units",
          exception);
    }
  }

  private static void applyCorporateAction(Map<ProjectionKey, List<FifoLot>> lotsByKey,
      HistoricalCorporateAction action) {
    boolean adjusted = false;
    for (Map.Entry<ProjectionKey, List<FifoLot>> entry : lotsByKey.entrySet()) {
      if (!entry.getKey().instrumentId().equals(action.instrumentId())) {
        continue;
      }
      List<FifoLot> adjustedLots = new ArrayList<>(entry.getValue().size());
      for (FifoLot lot : entry.getValue()) {
        if (lot.remainingQuantity().signum() == 0) {
          adjustedLots.add(lot);
          continue;
        }
        adjusted = true;
        adjustedLots.add(new FifoLot(lot.sourceTradeDetailId(), lot.sourceTransactionId(), lot.detailNo(),
            lot.occurredOn(), scaledQuantity(lot.openedQuantity(), action),
            scaledQuantity(lot.remainingQuantity(), action), lot.openedCostCent(), lot.remainingCostCent()));
      }
      entry.setValue(adjustedLots);
    }
    if (!adjusted) {
      throw new CorporateActionNoOpenPositionException();
    }
  }

  private static BigDecimal scaledQuantity(BigDecimal source, HistoricalCorporateAction action) {
    try {
      return Quantity.of(source.multiply(BigDecimal.valueOf(action.ratioNumerator()))
          .divide(BigDecimal.valueOf(action.ratioDenominator()), 8, RoundingMode.UNNECESSARY)
          .setScale(8, RoundingMode.UNNECESSARY));
    } catch (ArithmeticException exception) {
      throw new CorporateActionRatioException("corporate action ratio exceeds quantity precision", exception);
    }
  }

  private record ProjectionKey(String cashAccountId, String instrumentId,
                               com.personal.investment.ledger.domain.CurrencyCode currency) {
  }

  private record ReplayEvent(java.time.LocalDate occurredOn, String transactionId, int detailNo,
                             HistoricalSpotTrade trade, HistoricalCorporateAction action) {
    private static ReplayEvent trade(HistoricalSpotTrade trade) {
      return new ReplayEvent(trade.occurredOn(), trade.transactionId(), trade.detailNo(), trade, null);
    }

    private static ReplayEvent action(HistoricalCorporateAction action) {
      return new ReplayEvent(action.effectiveOn(), action.transactionId(), 0, null, action);
    }
  }
}
