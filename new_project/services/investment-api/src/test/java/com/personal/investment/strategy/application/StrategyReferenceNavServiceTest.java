package com.personal.investment.strategy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.strategy.domain.StrategyKey;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StrategyReferenceNavServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";

  @Test
  void appendsAUserMaintainedUsdReferenceNavForTheTwoUsdStrategies() {
    CapturingPort port = new CapturingPort();
    StrategyReferenceNavService service = new StrategyReferenceNavService(port,
        () -> "01K8D43J4YFN7X9R2B6C8M0V3N");
    Instant asOf = Instant.parse("2026-07-31T00:00:00Z");

    StrategyReferenceNav created = service.record(OWNER, StrategyKey.DEEP_PUT, CurrencyCode.USD, 66_600L, asOf,
        asOf.plusSeconds(86_400), "MANUAL");

    assertThat(created.strategyReferenceNavId()).isEqualTo("01K8D43J4YFN7X9R2B6C8M0V3N");
    assertThat(created.referenceNavCent()).isEqualTo(66_600L);
    assertThat(port.appended).isEqualTo(created);
  }

  @Test
  void rejectsNonUsdOrNonUsdStrategyAndAnInvalidValidityWindow() {
    StrategyReferenceNavService service = new StrategyReferenceNavService(new StrategyReferenceNavPort() {
      @Override
      public void append(StrategyReferenceNav referenceNav) {
      }

      @Override
      public Optional<StrategyReferenceNav> findApplicable(String ownerUserId, StrategyKey strategyKey,
          Instant asOfAt) {
        return Optional.empty();
      }
    }, () -> "ignored");
    Instant asOf = Instant.parse("2026-07-31T00:00:00Z");

    assertThatIllegalArgumentException().isThrownBy(() -> service.record(OWNER, StrategyKey.QQQ_GROWTH,
        CurrencyCode.CNY, 1L, asOf, asOf.plusSeconds(1), "MANUAL")).withMessageContaining("USD");
    assertThatIllegalArgumentException().isThrownBy(() -> service.record(OWNER, StrategyKey.HIGH_DIVIDEND,
        CurrencyCode.USD, 1L, asOf, asOf.plusSeconds(1), "MANUAL")).withMessageContaining("only");
    assertThatIllegalArgumentException().isThrownBy(() -> service.record(OWNER, StrategyKey.QQQ_GROWTH,
        CurrencyCode.USD, 1L, asOf, asOf.minusSeconds(1), "MANUAL")).withMessageContaining("validUntil");
  }

  private static final class CapturingPort implements StrategyReferenceNavPort {
    private StrategyReferenceNav appended;

    @Override
    public void append(StrategyReferenceNav referenceNav) {
      appended = referenceNav;
    }

    @Override
    public Optional<StrategyReferenceNav> findApplicable(String ownerUserId, StrategyKey strategyKey, Instant asOfAt) {
      return Optional.empty();
    }
  }
}
