package com.personal.investment.portfolio.infrastructure;

import com.personal.investment.ledger.application.HistoricalFifoPosition;
import com.personal.investment.ledger.application.SpotHistoryReplayer;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.portfolio.application.PortfolioAccountBalance;
import com.personal.investment.portfolio.application.PortfolioManualValuation;
import com.personal.investment.portfolio.application.PortfolioOpenPosition;
import com.personal.investment.portfolio.application.PortfolioOverviewPort;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MyBatisPortfolioOverviewAdapter implements PortfolioOverviewPort {
  private static final String MARGIN_AVAILABLE_PREFIX = "MRGAV:";
  private static final String MARGIN_LOCKED_PREFIX = "MRGLK:";

  private final PortfolioOverviewMapper mapper;
  private final SpotHistoryReplayer spotHistoryReplayer;

  public MyBatisPortfolioOverviewAdapter(PortfolioOverviewMapper mapper, SpotHistoryReplayer spotHistoryReplayer) {
    this.mapper = mapper;
    this.spotHistoryReplayer = spotHistoryReplayer;
  }

  @Override
  public List<PortfolioAccountBalance> findAccountBalances(String ownerUserId, LocalDate asOf) {
    Map<String, MutableAccountBalance> cash = new LinkedHashMap<>();
    List<PortfolioOverviewMapper.AccountBalanceRow> rows = mapper.findAccountBalances(ownerUserId, asOf);
    for (PortfolioOverviewMapper.AccountBalanceRow row : rows) {
      CurrencyCode currency = CurrencyCode.of(row.currency());
      if ("ASSET_CASH".equals(row.accountKind())) {
        if (cash.putIfAbsent(row.accountId(), new MutableAccountBalance(row.accountId(), currency, row.balanceCent())) != null) {
          throw new IllegalStateException("duplicate cash account in portfolio source");
        }
      }
    }
    for (PortfolioOverviewMapper.AccountBalanceRow row : rows) {
      if (!"ASSET_MARGIN".equals(row.accountKind())) {
        continue;
      }
      String cashAccountId = relatedCashAccountId(row.accountCode());
      MutableAccountBalance related = cash.get(cashAccountId);
      if (related == null || related.currency != CurrencyCode.of(row.currency())) {
        throw new IllegalStateException("margin account has no same-currency cash account");
      }
      related.addMargin(row.balanceCent());
    }
    return cash.values().stream().map(value -> new PortfolioAccountBalance(value.cashAccountId, value.currency,
        value.cashCent, value.marginCent)).toList();
  }

  @Override
  public List<PortfolioOpenPosition> findOpenPositions(String ownerUserId, LocalDate asOf) {
    List<PortfolioOpenPosition> positions = new ArrayList<>();
    for (PortfolioOverviewMapper.CashAccountRow cash : mapper.findCashAccounts(ownerUserId)) {
      CurrencyCode currency = CurrencyCode.of(cash.currency());
      for (HistoricalFifoPosition position : spotHistoryReplayer.positionsAt(ownerUserId, cash.accountId(), asOf)) {
        if (position.currency() != currency) {
          throw new IllegalStateException("FIFO position currency does not match its cash account");
        }
        positions.add(new PortfolioOpenPosition(cash.accountId(), position.instrumentId(), currency, position.quantity(),
            position.remainingCostCent()));
      }
      for (PortfolioOverviewMapper.FuturesPositionRow position : mapper.findFuturesPositions(ownerUserId,
          cash.accountId(), asOf)) {
        if (currency != CurrencyCode.CNY) {
          throw new IllegalStateException("futures position was associated with a non-CNY cash account");
        }
        positions.add(new PortfolioOpenPosition(cash.accountId(), position.instrumentId(), currency, position.quantity()));
      }
    }
    return List.copyOf(positions);
  }

  @Override
  public List<PortfolioManualValuation> findManualValuations(String ownerUserId, LocalDate asOf) {
    return mapper.findManualValuations(ownerUserId, asOf).stream().map(row -> new PortfolioManualValuation(
        row.instrumentId(), CurrencyCode.of(row.currency()), row.valuationDate(), row.unitPriceCent(),
        row.marketValueCent(), row.priority(), row.validUntil(), row.createdAt())).toList();
  }

  @Override
  public long currentLedgerVersion(String ownerUserId) {
    return mapper.currentLedgerVersion(ownerUserId);
  }

  private static String relatedCashAccountId(String accountCode) {
    if (accountCode != null && (accountCode.startsWith(MARGIN_AVAILABLE_PREFIX) || accountCode.startsWith(MARGIN_LOCKED_PREFIX))) {
      String cashAccountId = accountCode.substring(accountCode.indexOf(':') + 1);
      if (!cashAccountId.isBlank()) {
        return cashAccountId;
      }
    }
    throw new IllegalStateException("unknown portfolio margin account code");
  }

  private static final class MutableAccountBalance {
    private final String cashAccountId;
    private final CurrencyCode currency;
    private final long cashCent;
    private long marginCent;

    private MutableAccountBalance(String cashAccountId, CurrencyCode currency, long cashCent) {
      this.cashAccountId = cashAccountId;
      this.currency = currency;
      this.cashCent = cashCent;
    }

    private void addMargin(long amountCent) {
      try {
        marginCent = Math.addExact(marginCent, amountCent);
      } catch (ArithmeticException exception) {
        throw new IllegalStateException("portfolio margin amount overflow", exception);
      }
    }
  }
}
