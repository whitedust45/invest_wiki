package com.personal.investment.ledger.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LedgerTransactionMapper {
  @Insert("""
      INSERT INTO ledger_db.ledger_state
        (ledger_state_id, owner_user_id, ledger_version, created_at, updated_at)
      VALUES (#{ledgerStateId}, #{ownerUserId}, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
      ON DUPLICATE KEY UPDATE ledger_state_id = ledger_state_id
      """)
  int ensureLedgerState(@Param("ledgerStateId") String ledgerStateId,
      @Param("ownerUserId") String ownerUserId);

  @Select("""
      SELECT ledger_version
      FROM ledger_db.ledger_state
      WHERE owner_user_id = #{ownerUserId}
      FOR UPDATE
      """)
  long lockCurrentLedgerVersion(@Param("ownerUserId") String ownerUserId);

  @Update("""
      UPDATE ledger_db.ledger_state
      SET ledger_version = #{ledgerVersion}, updated_at = UTC_TIMESTAMP(3)
      WHERE owner_user_id = #{ownerUserId}
      """)
  int updateLedgerVersion(@Param("ownerUserId") String ownerUserId,
      @Param("ledgerVersion") long ledgerVersion);

  @Select("""
      SELECT p.posting_id AS postingId, p.account_id AS accountId, p.posting_no AS postingNo,
             p.posting_side AS postingSide, p.amount_cent AS amountCent, p.currency AS currency
      FROM ledger_db.ledger_posting p
      INNER JOIN ledger_db.ledger_transaction t ON t.transaction_id = p.transaction_id
      WHERE t.owner_user_id = #{ownerUserId}
      ORDER BY t.occurred_on, t.transaction_id, p.posting_no
      """)
  java.util.List<PostingFactRow> findPostingFactsByOwner(@Param("ownerUserId") String ownerUserId);

  @Select("""
      SELECT EXISTS(
        SELECT 1 FROM ledger_db.ledger_transaction WHERE owner_user_id = #{ownerUserId})
      """)
  boolean hasAnyTransactionByOwner(@Param("ownerUserId") String ownerUserId);

  @Insert("""
      INSERT INTO ledger_db.ledger_transaction
        (transaction_id, owner_user_id, transaction_type, strategy_key, operation_group_key, occurred_on, source_type,
         import_export_file_id, correction_root_transaction_id, reversal_of_transaction_id, revision_no, note,
         created_by_user_id, ledger_version, created_at)
      VALUES
        (#{transactionId}, #{ownerUserId}, #{transactionType}, #{strategyKey}, #{operationGroupKey}, #{occurredOn}, #{sourceType},
         #{importExportFileId}, #{correctionRootTransactionId}, #{reversalOfTransactionId}, #{revisionNo}, #{note}, #{createdByUserId},
         #{ledgerVersion}, UTC_TIMESTAMP(3))
      """)
  int insertTransaction(TransactionRow transaction);

  @Insert("""
      INSERT INTO ledger_db.ledger_posting
        (posting_id, transaction_id, account_id, posting_no, posting_side, amount_cent, currency, created_at)
      VALUES
        (#{postingId}, #{transactionId}, #{accountId}, #{postingNo}, #{postingSide}, #{amountCent},
         #{currency}, UTC_TIMESTAMP(3))
      """)
  int insertPosting(PostingRow posting);

  @Insert("""
      INSERT INTO ledger_db.ledger_trade_detail
        (trade_detail_id, transaction_id, detail_no, instrument_id, position_effect, quantity, unit_price_cent,
         price_points, contract_multiplier_cent, delivery_date, fee_cent, option_contract_multiplier, created_at)
      VALUES
        (#{tradeDetailId}, #{transactionId}, #{detailNo}, #{instrumentId}, #{positionEffect}, #{quantity},
         #{unitPriceCent}, #{pricePoints}, #{contractMultiplierCent}, #{deliveryDate}, #{feeCent},
         #{optionContractMultiplier}, UTC_TIMESTAMP(3))
      """)
  int insertTradeDetail(TradeDetailRow detail);

  record TransactionRow(String transactionId, String ownerUserId, String transactionType, String strategyKey,
                        String operationGroupKey,
                        java.time.LocalDate occurredOn, String sourceType,
                        String importExportFileId, String correctionRootTransactionId, String reversalOfTransactionId, int revisionNo, String note,
                        String createdByUserId, long ledgerVersion) {
  }

  record PostingRow(String postingId, String transactionId, String accountId, int postingNo,
                    String postingSide, long amountCent, String currency) {
  }

  record PostingFactRow(String postingId, String accountId, int postingNo, String postingSide,
                        long amountCent, String currency) {
  }

  record TradeDetailRow(String tradeDetailId, String transactionId, int detailNo, String instrumentId,
                        String positionEffect, java.math.BigDecimal quantity, Long unitPriceCent,
                        java.math.BigDecimal pricePoints, Long contractMultiplierCent,
                        java.time.LocalDate deliveryDate, long feeCent, Long optionContractMultiplier) {
  }
}
