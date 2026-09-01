package com.personal.investment.ledger.application;

import java.util.function.Supplier;

/** Request-scoped immutable attribution applied exactly while ledger facts are appended on this thread. */
public final class LedgerAppendMetadata {
  private static final ThreadLocal<Values> CURRENT = new ThreadLocal<>();

  private LedgerAppendMetadata() {
  }

  public static <T> T withStrategyKey(String strategyKey, Supplier<T> work) {
    return with(new Values(strategyKey, operationGroupKey()), work);
  }

  public static <T> T withOperationGroupKey(String operationGroupKey, Supplier<T> work) {
    return with(new Values(strategyKey(), operationGroupKey), work);
  }

  public static String strategyKey() {
    Values values = CURRENT.get();
    return values == null ? null : values.strategyKey();
  }

  public static String operationGroupKey() {
    Values values = CURRENT.get();
    return values == null ? null : values.operationGroupKey();
  }

  private static <T> T with(Values next, Supplier<T> work) {
    Values previous = CURRENT.get();
    try {
      CURRENT.set(next);
      return work.get();
    } finally {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }

  private record Values(String strategyKey, String operationGroupKey) {
  }
}
