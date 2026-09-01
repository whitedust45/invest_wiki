package com.personal.investment.portfolio.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PortfolioReconciliationQueryMapper {
  @Select("""
      SELECT reconciliation_id AS reconciliationId, cash_account_id AS cashAccountId,
             reconciliation_date AS reconciliationDate, broker_cash_cent AS brokerCashCent,
             ledger_cash_cent AS ledgerCashCent, cash_difference_cent AS cashDifferenceCent,
             cash_difference_direction AS cashDifferenceDirection, currency, status,
             source_ledger_version AS sourceLedgerVersion, created_at AS createdAt
      FROM portfolio_db.portfolio_reconciliation
      WHERE owner_user_id = #{ownerUserId}
        AND (#{cashAccountId} IS NULL OR cash_account_id = #{cashAccountId})
        AND (#{from} IS NULL OR reconciliation_date >= #{from})
        AND (#{to} IS NULL OR reconciliation_date <= #{to})
        AND (#{cursorDate} IS NULL
          OR reconciliation_date < #{cursorDate}
          OR (reconciliation_date = #{cursorDate} AND source_ledger_version < #{cursorLedgerVersion})
          OR (reconciliation_date = #{cursorDate} AND source_ledger_version = #{cursorLedgerVersion}
              AND created_at < #{cursorCreatedAt})
          OR (reconciliation_date = #{cursorDate} AND source_ledger_version = #{cursorLedgerVersion}
              AND created_at = #{cursorCreatedAt} AND reconciliation_id < #{cursorReconciliationId}))
      ORDER BY reconciliation_date DESC, source_ledger_version DESC, created_at DESC, reconciliation_id DESC
      LIMIT #{limit}
      """)
  List<Row> find(@Param("ownerUserId") String ownerUserId, @Param("cursorDate") LocalDate cursorDate,
                 @Param("cursorLedgerVersion") Long cursorLedgerVersion,
                 @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                 @Param("cursorReconciliationId") String cursorReconciliationId, @Param("limit") int limit,
                 @Param("cashAccountId") String cashAccountId, @Param("from") LocalDate from,
                 @Param("to") LocalDate to);

  @Select("""
      SELECT reconciliation_position_id AS reconciliationPositionId, instrument_id AS instrumentId,
             broker_quantity AS brokerQuantity, ledger_quantity AS ledgerQuantity,
             quantity_difference AS quantityDifference
      FROM portfolio_db.portfolio_reconciliation_position
      WHERE reconciliation_id = #{reconciliationId}
      ORDER BY instrument_id ASC
      """)
  List<PositionRow> findPositions(@Param("reconciliationId") String reconciliationId);

  record Row(String reconciliationId, String cashAccountId, LocalDate reconciliationDate, long brokerCashCent,
             long ledgerCashCent, long cashDifferenceCent, String cashDifferenceDirection, String currency,
             String status, long sourceLedgerVersion, LocalDateTime createdAt) {
  }

  record PositionRow(String reconciliationPositionId, String instrumentId, BigDecimal brokerQuantity,
                     BigDecimal ledgerQuantity, BigDecimal quantityDifference) {
  }
}
