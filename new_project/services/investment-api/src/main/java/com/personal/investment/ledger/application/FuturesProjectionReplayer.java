package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.FuturesLot;
import com.personal.investment.ledger.domain.InsufficientPositionException;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.PricePrecisionException;
import com.personal.investment.ledger.domain.Quantity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rebuilds all long futures lots from immutable open/close/settlement details after historical corrections. */
@Service
public class FuturesProjectionReplayer implements FuturesHistoryReplayer {
  private static final Comparator<HistoricalFuturesTrade> ORDER = Comparator
      .comparing(HistoricalFuturesTrade::occurredOn).thenComparing(HistoricalFuturesTrade::transactionId)
      .thenComparingInt(HistoricalFuturesTrade::detailNo);
  private static final Comparator<FuturesLot> FIFO_ORDER = Comparator.comparing(FuturesLot::openedOn)
      .thenComparing(FuturesLot::sourceTradeDetailId);

  private final FuturesHistoryPort historyPort;
  private final FuturesProjectionRebuildPort projectionPort;

  public FuturesProjectionReplayer(FuturesHistoryPort historyPort, FuturesProjectionRebuildPort projectionPort) {
    this.historyPort = historyPort;
    this.projectionPort = projectionPort;
  }

  @Override
  @Transactional
  public void rebuild(String ownerUserId, long sourceLedgerVersion) {
    try {
      Map<ProjectionKey, List<FuturesLot>> lotsByKey = new LinkedHashMap<>();
      for (HistoricalFuturesTrade event : historyPort.findAllByOwner(ownerUserId).stream().sorted(ORDER).toList()) {
        ProjectionKey key = new ProjectionKey(event.cashAccountId(), event.lockedMarginAccountId(), event.instrumentId(),
            event.currency());
        List<FuturesLot> lots = lotsByKey.computeIfAbsent(key, ignored -> new ArrayList<>());
        switch (event.transactionType()) {
          case FUTURES_OPEN -> applyOpen(lots, event);
          case FUTURES_CLOSE -> applyClose(lots, event);
          case FUTURES_DAILY_SETTLEMENT -> applySettlement(lots, event);
          default -> throw new IllegalStateException("unsupported future replay event");
        }
      }
      projectionPort.replaceOwnerProjection(ownerUserId, sourceLedgerVersion, lotsByKey.entrySet().stream()
          .map(entry -> new FuturesLotProjection(entry.getKey().cashAccountId(), entry.getKey().lockedMarginAccountId(),
              entry.getKey().instrumentId(), entry.getKey().currency(), entry.getValue())).toList());
    } catch (InsufficientPositionException | PricePrecisionException exception) {
      throw new ReplayInvariantViolationException("historical futures replay violates an immutable position invariant",
          exception);
    } catch (RuntimeException exception) {
      throw new ReplayInvariantViolationException("historical futures replay could not rebuild its lots", exception);
    }
  }

  private static void applyOpen(List<FuturesLot> lots, HistoricalFuturesTrade event) {
    BigDecimal quantity = Quantity.of(event.quantity());
    if (quantity.stripTrailingZeros().scale() > 0) {
      throw new IllegalArgumentException("historical futures open quantity must be whole lots");
    }
    try {
      if (event.initialMarginCent() < quantity.longValueExact()) {
        throw new IllegalArgumentException("historical initial margin cannot allocate one minor unit per lot");
      }
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("historical futures quantity exceeds supported range", exception);
    }
    lots.add(new FuturesLot(event.tradeDetailId(), event.occurredOn(), quantity, quantity, event.pricePoints(),
        event.pricePoints(), event.occurredOn(), event.contractMultiplierCent(), event.initialMarginCent(),
        event.initialMarginCent(), event.currency()));
  }

  private static void applyClose(List<FuturesLot> lots, HistoricalFuturesTrade event) {
    BigDecimal remaining = Quantity.of(event.quantity());
    List<FuturesLot> rebuilt = new ArrayList<>();
    for (FuturesLot lot : lots.stream().sorted(FIFO_ORDER).toList()) {
      if (remaining.signum() == 0) {
        rebuilt.add(lot);
        continue;
      }
      validateSnapshot(lot, event);
      BigDecimal consumed = remaining.min(lot.remainingQuantity());
      long releasedMargin = allocatedMargin(lot, consumed);
      BigDecimal remainingQuantity = lot.remainingQuantity().subtract(consumed);
      long remainingMargin = Math.subtractExact(lot.remainingInitialMarginCent(), releasedMargin);
      if (remainingQuantity.signum() > 0) {
        rebuilt.add(new FuturesLot(lot.sourceTradeDetailId(), lot.openedOn(), lot.openedQuantity(), remainingQuantity,
            lot.openPricePoints(), lot.lastSettlementPricePoints(), lot.lastSettlementOn(), lot.contractMultiplierCent(),
            lot.allocatedInitialMarginCent(), remainingMargin, lot.currency()));
      } else if (remainingMargin != 0) {
        throw new IllegalStateException("historical futures full close did not release all locked margin");
      }
      remaining = remaining.subtract(consumed);
    }
    if (remaining.signum() != 0) {
      throw new InsufficientPositionException();
    }
    lots.clear();
    lots.addAll(rebuilt);
  }

  private static void applySettlement(List<FuturesLot> lots, HistoricalFuturesTrade event) {
    if (lots.isEmpty()) {
      throw new IllegalArgumentException("historical futures settlement requires open lots");
    }
    BigDecimal openQuantity = lots.stream().map(FuturesLot::remainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (openQuantity.compareTo(Quantity.of(event.quantity())) != 0) {
      throw new IllegalArgumentException("historical settlement quantity does not equal its open lots");
    }
    List<FuturesLot> rebuilt = new ArrayList<>(lots.size());
    for (FuturesLot lot : lots) {
      validateSnapshot(lot, event);
      if (!event.occurredOn().isAfter(lot.lastSettlementOn())) {
        throw new IllegalArgumentException("historical settlement date is not strictly increasing");
      }
      rebuilt.add(new FuturesLot(lot.sourceTradeDetailId(), lot.openedOn(), lot.openedQuantity(),
          lot.remainingQuantity(), lot.openPricePoints(), event.pricePoints(), event.occurredOn(),
          lot.contractMultiplierCent(), lot.allocatedInitialMarginCent(), lot.remainingInitialMarginCent(),
          lot.currency()));
    }
    lots.clear();
    lots.addAll(rebuilt);
  }

  private static void validateSnapshot(FuturesLot lot, HistoricalFuturesTrade event) {
    if (lot.currency() != event.currency() || lot.contractMultiplierCent() != event.contractMultiplierCent()) {
      throw new IllegalArgumentException("historical futures contract snapshot does not match its opened lot");
    }
  }

  private static long allocatedMargin(FuturesLot lot, BigDecimal consumed) {
    if (consumed.compareTo(lot.remainingQuantity()) == 0) {
      return lot.remainingInitialMarginCent();
    }
    try {
      return BigDecimal.valueOf(lot.remainingInitialMarginCent()).multiply(consumed)
          .divide(lot.remainingQuantity(), 0, RoundingMode.DOWN).longValueExact();
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("historical futures margin allocation is invalid", exception);
    }
  }

  private record ProjectionKey(String cashAccountId, String lockedMarginAccountId, String instrumentId,
                               com.personal.investment.ledger.domain.CurrencyCode currency) {
  }
}
