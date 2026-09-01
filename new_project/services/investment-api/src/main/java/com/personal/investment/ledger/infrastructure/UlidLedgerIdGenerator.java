package com.personal.investment.ledger.infrastructure;

import com.personal.investment.identity.domain.UlidGenerator;
import com.personal.investment.ledger.application.LedgerIdGenerator;
import org.springframework.stereotype.Component;

@Component
public class UlidLedgerIdGenerator implements LedgerIdGenerator {
  @Override
  public String next() {
    return UlidGenerator.next();
  }
}
