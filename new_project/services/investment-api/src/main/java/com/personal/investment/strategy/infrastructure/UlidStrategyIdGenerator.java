package com.personal.investment.strategy.infrastructure;

import com.personal.investment.identity.domain.UlidGenerator;
import com.personal.investment.strategy.application.StrategyIdGenerator;
import org.springframework.stereotype.Component;

@Component
public class UlidStrategyIdGenerator implements StrategyIdGenerator {
  @Override
  public String next() {
    return UlidGenerator.next();
  }
}
