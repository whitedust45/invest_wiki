package com.personal.investment.ledger.application;

public record FuturesRollResult(String operationGroupKey, FuturesCloseResult closeResult, FuturesOpenResult openResult) {
}
