package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FifoLot;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpotProjectionReplayerTest {
  @Test
  void rebuildsLotsFromImmutableFactsInDateTransactionAndDetailOrder() {
    String cash = "01K8D43J4YFN7X9R2B6C8M0V3C";
    String instrument = "01K8D43J4YFN7X9R2B6C8M0V3I";
    HistoricalSpotTrade buy = trade("01K8D43J4YFN7X9R2B6C8M0V31", LedgerTransactionType.TRADE_BUY,
        LocalDate.of(2026, 7, 25), cash, instrument, "01K8D43J4YFN7X9R2B6C8M0V41", "2", 1_000, 5);
    HistoricalSpotTrade sell = trade("01K8D43J4YFN7X9R2B6C8M0V32", LedgerTransactionType.TRADE_SELL,
        LocalDate.of(2026, 7, 26), cash, instrument, "01K8D43J4YFN7X9R2B6C8M0V42", "1", 1_100, 0);
    CapturingProjectionPort projection = new CapturingProjectionPort();
    SpotProjectionReplayer replayer = new SpotProjectionReplayer(owner -> List.of(sell, buy), projection);

    replayer.rebuild("01K8D43J4YFN7X9R2B6C8M0V3P", 2);

    assertThat(projection.projections).singleElement().satisfies(item -> {
      assertThat(item.cashAccountId()).isEqualTo(cash);
      assertThat(item.instrumentId()).isEqualTo(instrument);
      assertThat(item.lots()).singleElement().satisfies(lot -> {
        assertThat(lot.remainingQuantity()).isEqualByComparingTo("1");
        assertThat(lot.remainingCostCent()).isEqualTo(1_003);
      });
    });
    assertThat(replayer.positionsAt("01K8D43J4YFN7X9R2B6C8M0V3P", cash, LocalDate.of(2026, 7, 25)))
        .containsExactly(new HistoricalFifoPosition(cash, instrument, CurrencyCode.USD, new BigDecimal("2"), 2_005L));
  }

  @Test
  void appliesStockSplitInHistoricalOrderWithoutChangingLotCost() {
    String cash = "01K8D43J4YFN7X9R2B6C8M0V3C";
    String instrument = "01K8D43J4YFN7X9R2B6C8M0V3I";
    HistoricalSpotTrade buy = trade("01K8D43J4YFN7X9R2B6C8M0V31", LedgerTransactionType.TRADE_BUY,
        LocalDate.of(2026, 7, 25), cash, instrument, "01K8D43J4YFN7X9R2B6C8M0V41", "3", 1_000, 5);
    HistoricalCorporateAction split = new HistoricalCorporateAction("01K8D43J4YFN7X9R2B6C8M0V32",
        LocalDate.of(2026, 7, 26), instrument, CorporateActionType.STOCK_SPLIT, 2, 1);
    CapturingProjectionPort projection = new CapturingProjectionPort();
    SpotHistoryPort history = new SpotHistoryPort() {
      @Override
      public List<HistoricalSpotTrade> findAllByOwner(String ownerUserId) {
        return List.of(buy);
      }

      @Override
      public List<HistoricalCorporateAction> findCorporateActionsByOwner(String ownerUserId) {
        return List.of(split);
      }
    };

    new SpotProjectionReplayer(history, projection).rebuild("01K8D43J4YFN7X9R2B6C8M0V3P", 2);

    assertThat(projection.projections).singleElement().satisfies(item -> assertThat(item.lots()).singleElement()
        .satisfies(lot -> {
          assertThat(lot.remainingQuantity()).isEqualByComparingTo("6");
          assertThat(lot.remainingCostCent()).isEqualTo(3_005);
        }));
  }

  @Test
  void rebuildsLongOptionLotsUsingTheImmutableContractMultiplierSnapshot() {
    String cash = "01K8D43J4YFN7X9R2B6C8M0V3C";
    String option = "01K8D43J4YFN7X9R2B6C8M0V3O";
    HistoricalSpotTrade open = optionTrade("01K8D43J4YFN7X9R2B6C8M0V31", LedgerTransactionType.OPTION_OPEN,
        LocalDate.of(2026, 8, 19), cash, option, "01K8D43J4YFN7X9R2B6C8M0V41", "2", 250L, 5, 100L);
    HistoricalSpotTrade close = optionTrade("01K8D43J4YFN7X9R2B6C8M0V32", LedgerTransactionType.OPTION_CLOSE,
        LocalDate.of(2026, 8, 20), cash, option, "01K8D43J4YFN7X9R2B6C8M0V42", "1", 300L, 10, 100L);
    HistoricalSpotTrade expire = optionTrade("01K8D43J4YFN7X9R2B6C8M0V33", LedgerTransactionType.OPTION_EXPIRE,
        LocalDate.of(2026, 8, 21), cash, option, "01K8D43J4YFN7X9R2B6C8M0V43", "1", null, 0, 100L);
    CapturingProjectionPort projection = new CapturingProjectionPort();

    new SpotProjectionReplayer(owner -> List.of(expire, close, open), projection)
        .rebuild("01K8D43J4YFN7X9R2B6C8M0V3P", 3);

    assertThat(projection.projections).singleElement().satisfies(item -> assertThat(item.lots()).singleElement()
        .satisfies(lot -> {
          assertThat(lot.openedCostCent()).isEqualTo(50_005L);
          assertThat(lot.remainingQuantity()).isZero();
          assertThat(lot.remainingCostCent()).isZero();
        }));
  }

  private static HistoricalSpotTrade trade(String transactionId, LedgerTransactionType type, LocalDate date,
      String cashAccountId, String instrumentId, String detailId, String quantity, long priceCent, long feeCent) {
    return new HistoricalSpotTrade(transactionId, type, date, cashAccountId, instrumentId, detailId, 1,
        new BigDecimal(quantity), priceCent, feeCent, null, CurrencyCode.USD);
  }

  private static HistoricalSpotTrade optionTrade(String transactionId, LedgerTransactionType type, LocalDate date,
      String cashAccountId, String instrumentId, String detailId, String quantity, Long priceCent, long feeCent,
      long contractMultiplier) {
    return new HistoricalSpotTrade(transactionId, type, date, cashAccountId, instrumentId, detailId, 1,
        new BigDecimal(quantity), priceCent, feeCent, contractMultiplier, CurrencyCode.USD);
  }

  private static final class CapturingProjectionPort implements SpotProjectionRebuildPort {
    private List<SpotLotProjection> projections = List.of();

    @Override
    public void replaceOwnerProjection(String ownerUserId, long sourceLedgerVersion, List<SpotLotProjection> projections) {
      this.projections = projections;
    }
  }
}
