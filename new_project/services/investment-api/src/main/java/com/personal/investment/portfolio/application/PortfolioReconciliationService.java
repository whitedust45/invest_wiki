package com.personal.investment.portfolio.application;

import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.domain.Quantity;
import com.personal.investment.market.application.InstrumentPort;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.portfolio.domain.CashDifferenceDirection;
import com.personal.investment.portfolio.domain.PortfolioReconciliation;
import com.personal.investment.portfolio.domain.ReconciliationPosition;
import com.personal.investment.portfolio.domain.ReconciliationStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioReconciliationService {
  private final PortfolioReconciliationPort reconciliationPort;
  private final InstrumentPort instrumentPort;
  private final LedgerIdGenerator idGenerator;

  public PortfolioReconciliationService(PortfolioReconciliationPort reconciliationPort, InstrumentPort instrumentPort,
      LedgerIdGenerator idGenerator) {
    this.reconciliationPort = reconciliationPort;
    this.instrumentPort = instrumentPort;
    this.idGenerator = idGenerator;
  }

  @Transactional
  public PortfolioReconciliation record(String ownerUserId, PortfolioReconciliationCommand command) {
    validateCommand(ownerUserId, command);
    LedgerReconciliationSnapshot snapshot = reconciliationPort.snapshot(ownerUserId, command.cashAccountId(),
        command.reconciliationDate());
    Map<String, BigDecimal> brokerPositions = validateBrokerPositions(command.positions(), snapshot);
    if (command.attachmentImportExportFileId() != null) {
      reconciliationPort.requireOwnedEvidence(ownerUserId, command.attachmentImportExportFileId());
    }
    long signedCashDifference = Math.subtractExact(command.brokerCashCent(), snapshot.cashCent());
    long cashDifference = Math.abs(signedCashDifference);
    CashDifferenceDirection direction = signedCashDifference == 0 ? CashDifferenceDirection.NONE
        : signedCashDifference > 0 ? CashDifferenceDirection.BROKER_GREATER : CashDifferenceDirection.LEDGER_GREATER;
    String reconciliationId = idGenerator.next();
    List<ReconciliationPosition> positions = union(brokerPositions, snapshot.positions());
    boolean matched = cashDifference == 0 && positions.stream().allMatch(position -> position.quantityDifference().signum() == 0);
    String discrepancyReason = normalizedReason(command.discrepancyReason());
    if (!matched && discrepancyReason == null) {
      throw new IllegalArgumentException("discrepancyReason is required when reconciliation needs review");
    }
    PortfolioReconciliation reconciliation = new PortfolioReconciliation(reconciliationId, ownerUserId,
        command.cashAccountId(), command.reconciliationDate(), command.brokerCashCent(), snapshot.cashCent(),
        cashDifference, direction, snapshot.currency(), matched ? ReconciliationStatus.MATCHED : ReconciliationStatus.NEEDS_REVIEW,
        discrepancyReason, command.attachmentImportExportFileId(), snapshot.sourceLedgerVersion(), positions);
    reconciliationPort.append(reconciliation);
    return reconciliation;
  }

  private Map<String, BigDecimal> validateBrokerPositions(List<BrokerPosition> positions,
      LedgerReconciliationSnapshot snapshot) {
    Map<String, BigDecimal> normalized = new LinkedHashMap<>();
    for (BrokerPosition position : positions) {
      if (position == null || position.instrumentId() == null || position.instrumentId().isBlank()) {
        throw new IllegalArgumentException("broker position is invalid");
      }
      BigDecimal quantity = Quantity.of(position.quantity());
      Instrument instrument = instrumentPort.findById(position.instrumentId())
          .orElseThrow(() -> new IllegalArgumentException("broker position instrument was not found"));
      if (instrument.nativeCurrency() != snapshot.currency()) {
        throw new IllegalArgumentException("broker position currency must match the selected cash account");
      }
      if (instrument.assetType() == AssetType.FUTURE && quantity.stripTrailingZeros().scale() > 0) {
        throw new IllegalArgumentException("futures broker position quantity must be a whole contract count");
      }
      if (normalized.putIfAbsent(position.instrumentId(), quantity) != null) {
        throw new IllegalArgumentException("broker positions must not contain duplicate instruments");
      }
    }
    return normalized;
  }

  private List<ReconciliationPosition> union(Map<String, BigDecimal> broker, Map<String, BigDecimal> ledger) {
    return new TreeSet<>(unionKeys(broker, ledger)).stream().map(instrumentId -> {
      BigDecimal brokerQuantity = broker.getOrDefault(instrumentId, BigDecimal.ZERO.setScale(8));
      BigDecimal ledgerQuantity = ledger.getOrDefault(instrumentId, BigDecimal.ZERO.setScale(8));
      return new ReconciliationPosition(idGenerator.next(), instrumentId, brokerQuantity, ledgerQuantity,
          brokerQuantity.subtract(ledgerQuantity).setScale(8, RoundingMode.UNNECESSARY));
    }).toList();
  }

  private static List<String> unionKeys(Map<String, BigDecimal> left, Map<String, BigDecimal> right) {
    return java.util.stream.Stream.concat(left.keySet().stream(), right.keySet().stream()).distinct().toList();
  }

  private static void validateCommand(String ownerUserId, PortfolioReconciliationCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank() || command == null || command.cashAccountId() == null
        || command.cashAccountId().isBlank() || command.reconciliationDate() == null || command.brokerCashCent() < 0
        || command.positions() == null) {
      throw new IllegalArgumentException("portfolio reconciliation command is invalid");
    }
    if (command.discrepancyReason() != null && command.discrepancyReason().length() > 1_000) {
      throw new IllegalArgumentException("discrepancyReason exceeds 1000 characters");
    }
  }

  private static String normalizedReason(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
