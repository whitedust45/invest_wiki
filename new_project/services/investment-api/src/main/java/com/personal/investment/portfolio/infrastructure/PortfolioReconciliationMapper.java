package com.personal.investment.portfolio.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PortfolioReconciliationMapper {
  @Select("""
      SELECT COALESCE(SUM(CASE posting.posting_side WHEN 'DEBIT' THEN posting.amount_cent ELSE -posting.amount_cent END), 0)
      FROM ledger_db.ledger_posting posting
      INNER JOIN ledger_db.ledger_transaction tx ON tx.transaction_id = posting.transaction_id
      WHERE tx.owner_user_id = #{ownerUserId}
        AND posting.account_id = #{cashAccountId}
        AND tx.occurred_on <= #{asOf}
      """)
  long cashBalance(@Param("ownerUserId") String ownerUserId, @Param("cashAccountId") String cashAccountId,
                   @Param("asOf") LocalDate asOf);

  @Select("""
      SELECT detail.instrument_id AS instrumentId,
             SUM(CASE detail.position_effect WHEN 'OPEN' THEN detail.quantity WHEN 'CLOSE' THEN -detail.quantity ELSE 0 END)
               AS quantity
      FROM ledger_db.ledger_transaction tx
      INNER JOIN ledger_db.ledger_trade_detail detail ON detail.transaction_id = tx.transaction_id
      WHERE tx.owner_user_id = #{ownerUserId}
        AND tx.occurred_on <= #{asOf}
        AND tx.transaction_type IN ('FUTURES_OPEN', 'FUTURES_CLOSE')
        AND EXISTS (
          SELECT 1
          FROM ledger_db.ledger_posting posting
          INNER JOIN ledger_db.ledger_account locked_margin ON locked_margin.account_id = posting.account_id
          WHERE posting.transaction_id = tx.transaction_id
            AND locked_margin.owner_user_id = #{ownerUserId}
            AND locked_margin.account_kind = 'ASSET_MARGIN'
            AND locked_margin.account_code = CONCAT('MRGLK:', #{cashAccountId})
        )
        AND NOT EXISTS (
          SELECT 1 FROM ledger_db.ledger_transaction reversal
          WHERE reversal.reversal_of_transaction_id = tx.transaction_id
            AND reversal.transaction_type = 'REVERSAL'
        )
      GROUP BY detail.instrument_id
      HAVING quantity > 0
      ORDER BY detail.instrument_id
      """)
  List<QuantityRow> futuresPositions(@Param("ownerUserId") String ownerUserId,
                                     @Param("cashAccountId") String cashAccountId,
                                     @Param("asOf") LocalDate asOf);

  @Select("""
      SELECT COALESCE((
        SELECT ledger_version FROM ledger_db.ledger_state WHERE owner_user_id = #{ownerUserId}
      ), 0)
      """)
  long currentLedgerVersion(@Param("ownerUserId") String ownerUserId);

  @Select("""
      SELECT EXISTS(
        SELECT 1
        FROM platform_db.import_export_file file
        WHERE file.import_export_file_id = #{attachmentImportExportFileId}
          AND file.owner_user_id = #{ownerUserId}
          AND file.direction = 'RECONCILIATION_EVIDENCE'
          AND file.status IN ('SCANNED', 'PREVIEWED', 'COMMITTED')
          AND NOT EXISTS (
            SELECT 1 FROM portfolio_db.portfolio_reconciliation reconciliation
            WHERE reconciliation.attachment_import_export_file_id = file.import_export_file_id
          )
      )
      """)
  boolean hasUnusedOwnedEvidence(@Param("ownerUserId") String ownerUserId,
                                 @Param("attachmentImportExportFileId") String attachmentImportExportFileId);

  @Insert("""
      INSERT INTO portfolio_db.portfolio_reconciliation
        (reconciliation_id, owner_user_id, cash_account_id, reconciliation_date, broker_cash_cent, ledger_cash_cent,
         cash_difference_cent, cash_difference_direction, currency, status, discrepancy_reason,
         attachment_import_export_file_id, source_ledger_version, created_by_user_id, created_at)
      VALUES
        (#{reconciliationId}, #{ownerUserId}, #{cashAccountId}, #{reconciliationDate}, #{brokerCashCent},
         #{ledgerCashCent}, #{cashDifferenceCent}, #{cashDifferenceDirection}, #{currency}, #{status},
         #{discrepancyReason}, #{attachmentImportExportFileId}, #{sourceLedgerVersion}, #{createdByUserId},
         UTC_TIMESTAMP(3))
      """)
  int insert(ReconciliationRow row);

  @Insert("""
      INSERT INTO portfolio_db.portfolio_reconciliation_position
        (reconciliation_position_id, reconciliation_id, instrument_id, broker_quantity, ledger_quantity,
         quantity_difference, created_at)
      VALUES
        (#{reconciliationPositionId}, #{reconciliationId}, #{instrumentId}, #{brokerQuantity}, #{ledgerQuantity},
         #{quantityDifference}, UTC_TIMESTAMP(3))
      """)
  int insertPosition(PositionRow row);

  record QuantityRow(String instrumentId, BigDecimal quantity) {
  }

  record ReconciliationRow(String reconciliationId, String ownerUserId, String cashAccountId,
                            LocalDate reconciliationDate, long brokerCashCent, long ledgerCashCent,
                            long cashDifferenceCent, String cashDifferenceDirection, String currency, String status,
                            String discrepancyReason, String attachmentImportExportFileId, long sourceLedgerVersion,
                            String createdByUserId) {
  }

  record PositionRow(String reconciliationPositionId, String reconciliationId, String instrumentId,
                     BigDecimal brokerQuantity, BigDecimal ledgerQuantity, BigDecimal quantityDifference) {
  }
}
