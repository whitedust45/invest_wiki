package com.personal.investment.strategy.application;

public enum StrategyScanStatus {
  QUEUED,
  RUNNING,
  SUCCEEDED,
  PARTIAL_SUCCEEDED,
  FAILED,
  SKIPPED_STALE
}
