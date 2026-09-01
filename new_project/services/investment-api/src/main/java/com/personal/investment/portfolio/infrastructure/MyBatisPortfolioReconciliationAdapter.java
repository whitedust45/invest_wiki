package com.personal.investment.portfolio.infrastructure;

import com.personal.investment.ledger.application.HistoricalFifoPosition;
import com.personal.investment.ledger.application.LedgerCommandAccountPort;
import com.personal.investment.ledger.application.SpotHistoryReplayer;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.portfolio.application.LedgerReconciliationSnapshot;
import com.personal.investment.portfolio.application.PortfolioReconciliationPort;
import com.personal.investment.portfolio.domain.PortfolioReconciliation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MyBatisPortfolioReconciliationAdapter implements PortfolioReconciliationPort {
  private final PortfolioReconciliationMapper mapper;
  private final LedgerCommandAccountPort accountPort;
  private final SpotHistoryReplayer spotHistoryReplayer;

  public MyBatisPortfolioReconciliationAdapter(PortfolioReconciliationMapper mapper, LedgerCommandAccountPort accountPort,
      SpotHistoryReplayer spotHistoryReplayer) {
    this.mapper = mapper;
    this.accountPort = accountPort;
    this.spotHistoryReplayer = spotHistoryReplayer;
  }

  @Override
  public LedgerReconciliationSnapshot snapshot(String ownerUserId, String cashAccountId, LocalDate asOf) {
    LedgerAccount cash = accountPort.findByIdAndOwner(cashAccountId, ownerUserId)
        .orElseThrow(() -> new IllegalArgumentException("cash account was not found"));
    if (cash.accountKind() != LedgerAccountKind.ASSET_CASH) {
      throw new IllegalArgumentException("reconciliation requires a cash account");
    }
    Map<String, BigDecimal> positions = new LinkedHashMap<>();
    for (HistoricalFifoPosition position : spotHistoryReplayer.positionsAt(ownerUserId, cashAccountId, asOf)) {
      if (position.currency() != cash.currency()) {
        throw new IllegalStateException("FIFO position currency does not match its cash account");
      }
      merge(positions, position.instrumentId(), position.quantity());
    }
    for (PortfolioReconciliationMapper.QuantityRow position : mapper.futuresPositions(ownerUserId, cashAccountId, asOf)) {
      if (cash.currency() != com.personal.investment.ledger.domain.CurrencyCode.CNY) {
        throw new IllegalStateException("futures position was associated with a non-CNY cash account");
      }
      merge(positions, position.instrumentId(), position.quantity());
    }
    return new LedgerReconciliationSnapshot(cash.currency(), mapper.cashBalance(ownerUserId, cashAccountId, asOf),
        positions, mapper.currentLedgerVersion(ownerUserId));
  }

  @Override
  public void append(PortfolioReconciliation reconciliation) {
    if (mapper.insert(new PortfolioReconciliationMapper.ReconciliationRow(reconciliation.reconciliationId(),
        reconciliation.ownerUserId(), reconciliation.cashAccountId(), reconciliation.reconciliationDate(),
        reconciliation.brokerCashCent(), reconciliation.ledgerCashCent(), reconciliation.cashDifferenceCent(),
        reconciliation.cashDifferenceDirection().name(), reconciliation.currency().name(), reconciliation.status().name(),
        reconciliation.discrepancyReason(), reconciliation.attachmentImportExportFileId(),
        reconciliation.sourceLedgerVersion(), reconciliation.ownerUserId())) != 1) {
      throw new IllegalStateException("portfolio reconciliation was not persisted");
    }
    for (var position : reconciliation.positions()) {
      if (mapper.insertPosition(new PortfolioReconciliationMapper.PositionRow(position.reconciliationPositionId(),
          reconciliation.reconciliationId(), position.instrumentId(), position.brokerQuantity(), position.ledgerQuantity(),
          position.quantityDifference())) != 1) {
        throw new IllegalStateException("portfolio reconciliation position was not persisted");
      }
    }
  }

  @Override
  public void requireOwnedEvidence(String ownerUserId, String attachmentImportExportFileId) {
    if (attachmentImportExportFileId == null || attachmentImportExportFileId.isBlank()
        || !mapper.hasUnusedOwnedEvidence(ownerUserId, attachmentImportExportFileId)) {
      throw new IllegalArgumentException("reconciliation evidence was not found, is not ready, or was already used");
    }
  }

  private static void merge(Map<String, BigDecimal> positions, String instrumentId, BigDecimal quantity) {
    positions.merge(instrumentId, quantity, BigDecimal::add);
  }
}
