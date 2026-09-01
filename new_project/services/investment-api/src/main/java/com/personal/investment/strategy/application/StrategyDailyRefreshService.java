package com.personal.investment.strategy.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Runs only against persisted rule, ledger and market inputs. It never pulls a market provider or creates trades.
 */
@Service
public class StrategyDailyRefreshService {
  private final StrategyWorkspacePort workspacePort;
  private final StrategyEvaluationService evaluationService;
  private final Clock clock;

  public StrategyDailyRefreshService(StrategyWorkspacePort workspacePort, StrategyEvaluationService evaluationService,
      Clock clock) {
    this.workspacePort = workspacePort;
    this.evaluationService = evaluationService;
    this.clock = clock;
  }

  public StrategyDailyRefreshResult refreshPersistedInputs() {
    List<StrategyActiveRule> activeRules = workspacePort.findActiveRulesForScheduledEvaluation();
    Instant asOfAt = Instant.now(clock);
    int succeeded = 0;
    List<String> failures = new ArrayList<>();
    for (StrategyActiveRule activeRule : activeRules) {
      try {
        evaluationService.evaluate(activeRule.ownerUserId(), activeRule.strategyKey(), asOfAt);
        succeeded++;
      } catch (RuntimeException exception) {
        failures.add(activeRule.ownerUserId() + ":" + activeRule.strategyKey().name() + ":"
            + exception.getClass().getSimpleName());
      }
    }
    return new StrategyDailyRefreshResult(activeRules.size(), succeeded, failures);
  }
}
