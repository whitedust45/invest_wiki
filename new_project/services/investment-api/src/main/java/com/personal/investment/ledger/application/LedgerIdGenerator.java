package com.personal.investment.ledger.application;

@FunctionalInterface
public interface LedgerIdGenerator {
  String next();
}
