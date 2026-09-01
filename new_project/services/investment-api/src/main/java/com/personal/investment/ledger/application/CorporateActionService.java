package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.TradableInstrument;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorporateActionService {
  private final LedgerTransactionPort transactionPort;
  private final LedgerIdGenerator idGenerator;
  private final CorporateActionPort corporateActionPort;
  private final LedgerTransactionEventPort transactionEventPort;
  private final SpotHistoryReplayer historyReplayer;
  private final SpotInstrumentPort instrumentPort;

  public CorporateActionService(LedgerTransactionPort transactionPort, LedgerIdGenerator idGenerator,
      CorporateActionPort corporateActionPort, LedgerTransactionEventPort transactionEventPort,
      SpotHistoryReplayer historyReplayer, SpotInstrumentPort instrumentPort) {
    this.transactionPort = transactionPort;
    this.idGenerator = idGenerator;
    this.corporateActionPort = corporateActionPort;
    this.transactionEventPort = transactionEventPort;
    this.historyReplayer = historyReplayer;
    this.instrumentPort = instrumentPort;
  }

  @Transactional
  public LedgerTransaction apply(String ownerUserId, CorporateActionCommand command) {
    return apply(ownerUserId, command, null);
  }

  @Transactional
  public LedgerTransaction applyReplacement(LedgerAppendContext context, CorporateActionCommand command) {
    Objects.requireNonNull(context, "context must not be null");
    return apply(context.ownerUserId(), command, context);
  }

  private LedgerTransaction apply(String ownerUserId, CorporateActionCommand command, LedgerAppendContext context) {
    validateCommand(ownerUserId, command);
    long lockedVersion = context == null ? transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next()) : 0;
    long ledgerVersion = context == null ? transactionPort.reserveNextLedgerVersion(ownerUserId, lockedVersion)
        : context.ledgerVersion();
    String transactionId = idGenerator.next();
    LedgerTransaction transaction = context == null
        ? LedgerTransaction.original(transactionId, ownerUserId, LedgerTransactionType.CORPORATE_ACTION,
            command.effectiveOn(), ledgerVersion, command.note(), List.of())
        : LedgerTransaction.replacement(transactionId, ownerUserId, LedgerTransactionType.CORPORATE_ACTION,
            command.effectiveOn(), context.correctionRootTransactionId(), context.revisionNo(), ledgerVersion,
            command.note(), List.of(), List.of());
    transactionPort.append(transaction);
    corporateActionPort.insert(new CorporateActionDetail(idGenerator.next(), transactionId, command.instrumentId(),
        command.actionType(), command.effectiveOn(), command.ratioNumerator(), command.ratioDenominator()));
    transactionEventPort.recordAppended(transaction);
    historyReplayer.rebuild(ownerUserId, ledgerVersion);
    return transaction;
  }

  /** Uses a maximum lexical ULID only for deterministic same-day preview ordering; it is never persisted. */
  public void preview(String ownerUserId, CorporateActionCommand command) {
    validateCommand(ownerUserId, command);
    historyReplayer.validateCorporateAction(ownerUserId, new HistoricalCorporateAction(
        "ZZZZZZZZZZZZZZZZZZZZZZZZZZ", command.effectiveOn(), command.instrumentId(), command.actionType(),
        command.ratioNumerator(), command.ratioDenominator()));
  }

  private void validateCommand(String ownerUserId, CorporateActionCommand command) {
    require(ownerUserId, "ownerUserId");
    Objects.requireNonNull(command, "command must not be null");
    require(command.instrumentId(), "instrumentId");
    Objects.requireNonNull(command.effectiveOn(), "effectiveOn must not be null");
    Objects.requireNonNull(command.actionType(), "actionType must not be null");
    if (command.ratioNumerator() <= 0 || command.ratioDenominator() <= 0) {
      throw new IllegalArgumentException("corporate action ratio must be positive");
    }
    TradableInstrument instrument = instrumentPort.findById(command.instrumentId())
        .orElseThrow(() -> new CorporateActionUnsupportedException(
            "corporate action only supports active EQUITY or ETF instruments"));
    if (instrument.instrumentId().isBlank()) {
      throw new CorporateActionUnsupportedException("corporate action instrument is invalid");
    }
  }

  private static void require(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
