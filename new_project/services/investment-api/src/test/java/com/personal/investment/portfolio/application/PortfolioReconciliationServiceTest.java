package com.personal.investment.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.market.domain.InstrumentStatus;
import com.personal.investment.portfolio.domain.CashDifferenceDirection;
import com.personal.investment.portfolio.domain.PortfolioReconciliation;
import com.personal.investment.portfolio.domain.ReconciliationPosition;
import com.personal.investment.portfolio.domain.ReconciliationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortfolioReconciliationServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String CASH = "01K8D43J4YFN7X9R2B6C8M0V3C";
  private static final String APPLE = "01K8D43J4YFN7X9R2B6C8M0V3A";
  private static final String QQQ = "01K8D43J4YFN7X9R2B6C8M0V3Q";
  private static final String SPY = "01K8D43J4YFN7X9R2B6C8M0V3S";

  @Test
  void persistsTheCompleteBrokerAndLedgerPositionUnionWithoutAutoAdjusting() {
    CapturingPort port = new CapturingPort(new LedgerReconciliationSnapshot(CurrencyCode.USD, 9_000,
        Map.of(APPLE, new BigDecimal("1"), QQQ, new BigDecimal("2")), 7));
    PortfolioReconciliationService service = new PortfolioReconciliationService(port, instruments(), new Ids());

    PortfolioReconciliation result = service.record(OWNER, new PortfolioReconciliationCommand(CASH,
        LocalDate.of(2026, 8, 21), 10_000, List.of(new BrokerPosition(APPLE, new BigDecimal("1")),
        new BrokerPosition(SPY, new BigDecimal("3"))), null, "券商导出核对"));

    assertThat(result.status()).isEqualTo(ReconciliationStatus.NEEDS_REVIEW);
    assertThat(result.cashDifferenceCent()).isEqualTo(1_000L);
    assertThat(result.cashDifferenceDirection()).isEqualTo(CashDifferenceDirection.BROKER_GREATER);
    assertThat(result.sourceLedgerVersion()).isEqualTo(7L);
    assertThat(result.positions()).containsExactlyInAnyOrder(
        new ReconciliationPosition("01K8D43J4YFN7X9R2B6C8M0V01", APPLE, new BigDecimal("1"),
            new BigDecimal("1"), BigDecimal.ZERO.setScale(8)),
        new ReconciliationPosition("01K8D43J4YFN7X9R2B6C8M0V02", QQQ, BigDecimal.ZERO.setScale(8),
            new BigDecimal("2"), new BigDecimal("-2.00000000")),
        new ReconciliationPosition("01K8D43J4YFN7X9R2B6C8M0V03", SPY, new BigDecimal("3"),
            BigDecimal.ZERO.setScale(8), new BigDecimal("3.00000000")));
    assertThat(port.persisted).containsExactly(result);
  }

  @Test
  void rejectsDuplicateOrCrossCurrencyBrokerPositionsBeforePersistingAnything() {
    CapturingPort port = new CapturingPort(new LedgerReconciliationSnapshot(CurrencyCode.USD, 9_000, Map.of(), 7));
    PortfolioReconciliationService service = new PortfolioReconciliationService(port, instruments(), new Ids());

    assertThatThrownBy(() -> service.record(OWNER, new PortfolioReconciliationCommand(CASH,
        LocalDate.of(2026, 8, 21), 9_000, List.of(new BrokerPosition(APPLE, new BigDecimal("1")),
        new BrokerPosition(APPLE, new BigDecimal("2"))), null, null)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
    assertThatThrownBy(() -> service.record(OWNER, new PortfolioReconciliationCommand(CASH,
        LocalDate.of(2026, 8, 21), 9_000, List.of(new BrokerPosition("01K8D43J4YFN7X9R2B6C8M0V3C",
        new BigDecimal("1"))), null, null)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("currency");
    assertThat(port.persisted).isEmpty();
  }

  @Test
  void requiresAnExplicitReasonWheneverCashOrPositionDifferencesNeedReview() {
    CapturingPort port = new CapturingPort(new LedgerReconciliationSnapshot(CurrencyCode.USD, 9_000,
        Map.of(APPLE, new BigDecimal("1")), 7));
    PortfolioReconciliationService service = new PortfolioReconciliationService(port, instruments(), new Ids());

    assertThatThrownBy(() -> service.record(OWNER, new PortfolioReconciliationCommand(CASH,
        LocalDate.of(2026, 8, 21), 9_001, List.of(new BrokerPosition(APPLE, new BigDecimal("1"))), null, null)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("discrepancyReason");
    assertThat(port.persisted).isEmpty();
  }

  private static InstrumentPort instruments() {
    return new InstrumentPort() {
      @Override
      public Optional<Instrument> findByNaturalKey(String market, String exchange, String symbol) {
        return Optional.empty();
      }

      @Override
      public Optional<Instrument> findById(String instrumentId) {
        if ("01K8D43J4YFN7X9R2B6C8M0V3C".equals(instrumentId)) {
          return Optional.of(instrument(instrumentId, CurrencyCode.CNY));
        }
        return List.of(APPLE, QQQ, SPY).contains(instrumentId) ? Optional.of(instrument(instrumentId, CurrencyCode.USD))
            : Optional.empty();
      }

      @Override
      public void insert(Instrument instrument, String futureContractId, String optionContractId) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static Instrument instrument(String id, CurrencyCode currency) {
    return new Instrument(id, "US", "NASDAQ", id, id, AssetType.ETF, currency, null,
        InstrumentStatus.ACTIVE, null, null);
  }

  private static final class CapturingPort implements PortfolioReconciliationPort {
    private final LedgerReconciliationSnapshot snapshot;
    private final List<PortfolioReconciliation> persisted = new ArrayList<>();

    private CapturingPort(LedgerReconciliationSnapshot snapshot) {
      this.snapshot = snapshot;
    }

    @Override
    public LedgerReconciliationSnapshot snapshot(String ownerUserId, String cashAccountId, LocalDate asOf) {
      return snapshot;
    }

    @Override
    public void append(PortfolioReconciliation reconciliation) {
      persisted.add(reconciliation);
    }

    @Override
    public void requireOwnedEvidence(String ownerUserId, String attachmentImportExportFileId) {
      throw new AssertionError("test does not provide attachment evidence");
    }
  }

  private static final class Ids implements LedgerIdGenerator {
    private int sequence;

    @Override
    public String next() {
      return "01K8D43J4YFN7X9R2B6C8M0V" + String.format("%02d", sequence++);
    }
  }
}
