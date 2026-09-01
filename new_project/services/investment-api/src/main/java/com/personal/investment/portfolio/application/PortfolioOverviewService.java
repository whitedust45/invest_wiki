package com.personal.investment.portfolio.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.Instrument;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds a native-currency-only read model from replayed facts and immutable manual valuations. */
@Service
public class PortfolioOverviewService {
  private final PortfolioOverviewPort overviewPort;
  private final InstrumentPort instrumentPort;
  private final Clock clock;

  public PortfolioOverviewService(PortfolioOverviewPort overviewPort, InstrumentPort instrumentPort, Clock clock) {
    this.overviewPort = overviewPort;
    this.instrumentPort = instrumentPort;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PortfolioOverview summary(String ownerUserId, LocalDate asOf) {
    Snapshot snapshot = snapshot(ownerUserId, asOf);
    return new PortfolioOverview(build(snapshot, null, null, true));
  }

  @Transactional(readOnly = true)
  public PortfolioCurrencyOverview positions(String ownerUserId, CurrencyCode currency, String cashAccountId,
      LocalDate asOf) {
    Objects.requireNonNull(currency, "currency must not be null");
    Snapshot snapshot = snapshot(ownerUserId, asOf);
    if (cashAccountId != null && snapshot.accounts().stream().noneMatch(account -> account.cashAccountId().equals(cashAccountId)
        && account.currency() == currency)) {
      throw new PortfolioResourceNotFoundException();
    }
    return build(snapshot, currency, cashAccountId, false).stream().findFirst()
        .orElseGet(() -> empty(currency, asOf, snapshot.ledgerVersion()));
  }

  private Snapshot snapshot(String ownerUserId, LocalDate asOf) {
    if (ownerUserId == null || ownerUserId.isBlank() || asOf == null) {
      throw new IllegalArgumentException("portfolio overview query is invalid");
    }
    return new Snapshot(List.copyOf(overviewPort.findAccountBalances(ownerUserId, asOf)),
        List.copyOf(overviewPort.findOpenPositions(ownerUserId, asOf)),
        List.copyOf(overviewPort.findManualValuations(ownerUserId, asOf)), overviewPort.currentLedgerVersion(ownerUserId),
        asOf);
  }

  private List<PortfolioCurrencyOverview> build(Snapshot snapshot, CurrencyCode requestedCurrency,
      String requestedCashAccountId, boolean includeUserInstrumentTotals) {
    Map<String, PortfolioAccountBalance> accountsById = new HashMap<>();
    Map<CurrencyCode, List<PortfolioAccountBalance>> accountsByCurrency = new EnumMap<>(CurrencyCode.class);
    for (PortfolioAccountBalance account : snapshot.accounts()) {
      PortfolioAccountBalance duplicate = accountsById.putIfAbsent(account.cashAccountId(), account);
      if (duplicate != null) {
        throw new IllegalStateException("duplicate cash account in portfolio overview source");
      }
      accountsByCurrency.computeIfAbsent(account.currency(), ignored -> new ArrayList<>()).add(account);
    }
    Map<CurrencyCode, List<PortfolioOpenPosition>> positionsByCurrency = new EnumMap<>(CurrencyCode.class);
    for (PortfolioOpenPosition position : snapshot.positions()) {
      PortfolioAccountBalance account = accountsById.get(position.cashAccountId());
      if (account == null || account.currency() != position.currency()) {
        throw new IllegalStateException("portfolio position cash account is missing or has another currency");
      }
      positionsByCurrency.computeIfAbsent(position.currency(), ignored -> new ArrayList<>()).add(position);
    }
    Set<CurrencyCode> currencies = new HashSet<>(accountsByCurrency.keySet());
    currencies.addAll(positionsByCurrency.keySet());
    if (requestedCurrency != null) {
      currencies.retainAll(Set.of(requestedCurrency));
    }
    List<PortfolioCurrencyOverview> views = new ArrayList<>();
    for (CurrencyCode currency : CurrencyCode.values()) {
      if (!currencies.contains(currency)) {
        continue;
      }
      List<PortfolioAccountBalance> accounts = accountsByCurrency.getOrDefault(currency, List.of()).stream()
          .filter(account -> requestedCashAccountId == null || account.cashAccountId().equals(requestedCashAccountId)).toList();
      List<PortfolioOpenPosition> positions = positionsByCurrency.getOrDefault(currency, List.of()).stream()
          .filter(position -> requestedCashAccountId == null || position.cashAccountId().equals(requestedCashAccountId)).toList();
      if (requestedCashAccountId != null && accounts.isEmpty()) {
        continue;
      }
      views.add(buildCurrency(currency, accounts, positions, snapshot.valuations(), snapshot.asOf(), snapshot.ledgerVersion(),
          includeUserInstrumentTotals));
    }
    return views;
  }

  private PortfolioCurrencyOverview buildCurrency(CurrencyCode currency, List<PortfolioAccountBalance> accounts,
      List<PortfolioOpenPosition> positions, List<PortfolioManualValuation> valuations, LocalDate asOf,
      long ledgerVersion, boolean includeUserInstrumentTotals) {
    long cashCent = sumAccounts(accounts, false);
    long marginCent = sumAccounts(accounts, true);
    if (positions.isEmpty()) {
      return new PortfolioCurrencyOverview(currency, cashCent, marginCent, 0L, add(cashCent, marginCent), List.of(),
          asOf, ledgerVersion, PortfolioValuationStatus.NO_OPEN_POSITION);
    }
    Map<String, List<PortfolioOpenPosition>> byInstrument = new LinkedHashMap<>();
    for (PortfolioOpenPosition position : positions) {
      byInstrument.computeIfAbsent(position.instrumentId(), ignored -> new ArrayList<>()).add(position);
    }
    Map<String, List<PortfolioManualValuation>> valuationsByInstrument = new HashMap<>();
    for (PortfolioManualValuation valuation : valuations) {
      if (valuation.currency() == currency && !valuation.valuationDate().isAfter(asOf)) {
        valuationsByInstrument.computeIfAbsent(valuation.instrumentId(), ignored -> new ArrayList<>()).add(valuation);
      }
    }
    long marketValueCent = 0L;
    boolean hasIncludedValue = false;
    boolean hasExcludedUserTotal = false;
    boolean hasUnavailableValue = false;
    List<PortfolioPositionView> positionViews = new ArrayList<>();
    for (Map.Entry<String, List<PortfolioOpenPosition>> entry : byInstrument.entrySet()) {
      InstrumentEvaluation evaluation = evaluateInstrument(entry.getKey(), currency, entry.getValue(),
          valuationsByInstrument.getOrDefault(entry.getKey(), List.of()), includeUserInstrumentTotals);
      positionViews.addAll(evaluation.positions());
      if (evaluation.includedMarketValueCent() != null) {
        marketValueCent = add(marketValueCent, evaluation.includedMarketValueCent());
        hasIncludedValue = true;
      }
      hasExcludedUserTotal |= evaluation.userTotalExcluded();
      hasUnavailableValue |= evaluation.unavailable();
    }
    PortfolioValuationStatus status;
    Long exposedMarketValue;
    Long netAssetValue;
    if (hasUnavailableValue) {
      status = hasIncludedValue ? PortfolioValuationStatus.PARTIALLY_UNVALUED : PortfolioValuationStatus.UNVALUED;
      exposedMarketValue = null;
      netAssetValue = null;
    } else if (hasExcludedUserTotal) {
      status = PortfolioValuationStatus.MANUAL_TOTAL_UNALLOCATED;
      exposedMarketValue = null;
      netAssetValue = null;
    } else {
      status = PortfolioValuationStatus.MANUAL;
      exposedMarketValue = marketValueCent;
      netAssetValue = add(add(cashCent, marginCent), marketValueCent);
    }
    return new PortfolioCurrencyOverview(currency, cashCent, marginCent, exposedMarketValue, netAssetValue,
        positionViews, asOf, ledgerVersion, status);
  }

  private InstrumentEvaluation evaluateInstrument(String instrumentId, CurrencyCode currency,
      List<PortfolioOpenPosition> positions, List<PortfolioManualValuation> candidates,
      boolean includeUserInstrumentTotals) {
    Optional<Instrument> instrument = instrumentPort.findById(instrumentId);
    if (instrument.isEmpty() || instrument.get().nativeCurrency() != currency) {
      return unavailable(positions, PortfolioPositionValuationStatus.UNVALUED);
    }
    if (instrument.get().assetType() == AssetType.FUTURE) {
      return unavailable(positions, PortfolioPositionValuationStatus.FUTURES_SETTLEMENT_ONLY);
    }
    Optional<PortfolioManualValuation> selected = selectUsable(candidates, Instant.now(clock));
    if (selected.isEmpty()) {
      PortfolioPositionValuationStatus status = candidates.isEmpty()
          ? PortfolioPositionValuationStatus.UNVALUED : PortfolioPositionValuationStatus.EXPIRED;
      return unavailable(positions, status);
    }
    PortfolioManualValuation valuation = selected.get();
    if (valuation.marketValueCent() != null) {
      List<PortfolioPositionView> views = positions.stream().map(position -> new PortfolioPositionView(
          position.cashAccountId(), position.instrumentId(), position.currency(), position.quantity(), null,
          position.costCent(), null, PortfolioPositionValuationStatus.MANUAL_TOTAL_UNALLOCATED)).toList();
      return new InstrumentEvaluation(views, includeUserInstrumentTotals ? valuation.marketValueCent() : null,
          !includeUserInstrumentTotals, false);
    }
    long multiplier = multiplier(instrument.get());
    List<PortfolioPositionView> views = new ArrayList<>();
    long total = 0L;
    try {
      for (PortfolioOpenPosition position : positions) {
        long value = exactMarketValue(position.quantity(), multiplier, valuation.unitPriceCent());
        total = add(total, value);
        Long unrealizedPnlCent = position.costCent() == null ? null : Math.subtractExact(value, position.costCent());
        views.add(new PortfolioPositionView(position.cashAccountId(), position.instrumentId(), position.currency(),
            position.quantity(), value, position.costCent(), unrealizedPnlCent,
            PortfolioPositionValuationStatus.MANUAL_UNIT_PRICE));
      }
    } catch (ArithmeticException exception) {
      return unavailable(positions, PortfolioPositionValuationStatus.PRECISION_UNAVAILABLE);
    }
    return new InstrumentEvaluation(views, total, false, false);
  }

  private static long multiplier(Instrument instrument) {
    return switch (instrument.assetType()) {
      case EQUITY, ETF -> 1L;
      case OPTION -> instrument.optionSpecification().contractMultiplier();
      case INDEX, FUTURE -> throw new IllegalStateException("index and futures must not be unit-price valued");
    };
  }

  private static Optional<PortfolioManualValuation> selectUsable(List<PortfolioManualValuation> candidates,
      Instant now) {
    Comparator<PortfolioManualValuation> order = Comparator
        .comparingInt(PortfolioManualValuation::priority).reversed()
        .thenComparing(PortfolioManualValuation::valuationDate, Comparator.reverseOrder())
        .thenComparing(PortfolioManualValuation::createdAt, Comparator.reverseOrder());
    return candidates.stream().filter(candidate -> candidate.validUntil() == null || candidate.validUntil().isAfter(now))
        .min(order);
  }

  private static InstrumentEvaluation unavailable(List<PortfolioOpenPosition> positions,
      PortfolioPositionValuationStatus status) {
    return new InstrumentEvaluation(positions.stream().map(position -> new PortfolioPositionView(position.cashAccountId(),
        position.instrumentId(), position.currency(), position.quantity(), null, position.costCent(), null, status)).toList(),
        null, false, true);
  }

  private static long exactMarketValue(BigDecimal quantity, long multiplier, long unitPriceCent) {
    return quantity.multiply(BigDecimal.valueOf(multiplier)).multiply(BigDecimal.valueOf(unitPriceCent)).longValueExact();
  }

  private static long sumAccounts(List<PortfolioAccountBalance> accounts, boolean margin) {
    long sum = 0L;
    for (PortfolioAccountBalance account : accounts) {
      sum = add(sum, margin ? account.marginCent() : account.cashCent());
    }
    return sum;
  }

  private static long add(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("portfolio amount overflow", exception);
    }
  }

  private static PortfolioCurrencyOverview empty(CurrencyCode currency, LocalDate asOf, long ledgerVersion) {
    return new PortfolioCurrencyOverview(currency, 0L, 0L, 0L, 0L, List.of(), asOf, ledgerVersion,
        PortfolioValuationStatus.NO_OPEN_POSITION);
  }

  private record Snapshot(List<PortfolioAccountBalance> accounts, List<PortfolioOpenPosition> positions,
                          List<PortfolioManualValuation> valuations, long ledgerVersion, LocalDate asOf) {
  }

  private record InstrumentEvaluation(List<PortfolioPositionView> positions, Long includedMarketValueCent,
                                      boolean userTotalExcluded, boolean unavailable) {
  }
}
