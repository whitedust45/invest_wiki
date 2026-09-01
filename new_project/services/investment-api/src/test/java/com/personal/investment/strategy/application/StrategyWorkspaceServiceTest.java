package com.personal.investment.strategy.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.strategy.domain.StrategyKey;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StrategyWorkspaceServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";

  @Test
  void exposesAllFourStrategyCardsEvenBeforeTheUserHasCreatedAnyRuleOrLedgerFact() {
    StrategyWorkspaceService service = new StrategyWorkspaceService(new EmptyWorkspacePort());

    List<StrategyCard> cards = service.listCards(OWNER);

    assertThat(cards).extracting(StrategyCard::strategyKey)
        .containsExactly(StrategyKey.HIGH_DIVIDEND, StrategyKey.QQQ_GROWTH, StrategyKey.IC_IM, StrategyKey.DEEP_PUT);
    assertThat(cards).allSatisfy(card -> {
      assertThat(card.status()).isEqualTo(StrategyEvaluationStatus.BLOCKED);
      assertThat(card.activeRuleVersionId()).isNull();
      assertThat(card.message()).isEqualTo("等待基础数据");
    });
  }

  private static final class EmptyWorkspacePort implements StrategyWorkspacePort {
    @Override
    public Optional<StrategyActiveRule> findActiveRule(String ownerUserId, StrategyKey strategyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<StrategyEvaluation> findLatestEvaluation(String ownerUserId, StrategyKey strategyKey) {
      return Optional.empty();
    }
  }
}
