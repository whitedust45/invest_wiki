package com.personal.investment.ledger.infrastructure;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LedgerTransactionQueryMapper {
  @Select("""
      SELECT t.transaction_id AS transactionId, t.transaction_type AS transactionType, t.occurred_on AS occurredOn,
             MIN(p.currency) AS currency, t.ledger_version AS ledgerVersion, t.source_type AS sourceType,
             t.import_export_file_id AS importExportFileId
      FROM ledger_db.ledger_transaction t
      LEFT JOIN ledger_db.ledger_posting p ON p.transaction_id = t.transaction_id
      WHERE t.owner_user_id = #{ownerUserId}
        AND (#{accountId} IS NULL OR EXISTS (
          SELECT 1 FROM ledger_db.ledger_posting account_posting
          WHERE account_posting.transaction_id = t.transaction_id AND account_posting.account_id = #{accountId}))
        AND (#{instrumentId} IS NULL OR EXISTS (
          SELECT 1 FROM ledger_db.ledger_trade_detail detail
          WHERE detail.transaction_id = t.transaction_id AND detail.instrument_id = #{instrumentId})
          OR EXISTS (
          SELECT 1 FROM ledger_db.ledger_corporate_action action
          WHERE action.transaction_id = t.transaction_id AND action.instrument_id = #{instrumentId}))
        AND (#{transactionType} IS NULL OR t.transaction_type = #{transactionType})
        AND (#{strategyKey} IS NULL OR t.strategy_key = #{strategyKey})
        AND (#{search} IS NULL
          OR t.transaction_type LIKE CONCAT('%', #{search}, '%')
          OR t.note LIKE CONCAT('%', #{search}, '%')
          OR EXISTS (
            SELECT 1 FROM ledger_db.ledger_trade_detail search_detail
            JOIN market_db.instrument search_instrument ON search_instrument.instrument_id = search_detail.instrument_id
            WHERE search_detail.transaction_id = t.transaction_id
              AND (search_instrument.symbol LIKE CONCAT('%', #{search}, '%')
                OR search_instrument.display_name LIKE CONCAT('%', #{search}, '%')))
          OR EXISTS (
            SELECT 1 FROM ledger_db.ledger_corporate_action search_action
            JOIN market_db.instrument search_instrument ON search_instrument.instrument_id = search_action.instrument_id
            WHERE search_action.transaction_id = t.transaction_id
              AND (search_instrument.symbol LIKE CONCAT('%', #{search}, '%')
                OR search_instrument.display_name LIKE CONCAT('%', #{search}, '%'))))
        AND (#{from} IS NULL OR t.occurred_on >= #{from})
        AND (#{to} IS NULL OR t.occurred_on <= #{to})
        AND (#{cursorOccurredOn} IS NULL
          OR t.occurred_on < #{cursorOccurredOn}
          OR (t.occurred_on = #{cursorOccurredOn} AND t.transaction_id < #{cursorTransactionId}))
      GROUP BY t.transaction_id, t.transaction_type, t.occurred_on, t.ledger_version, t.source_type,
               t.import_export_file_id
      ORDER BY t.occurred_on DESC, t.transaction_id DESC
      LIMIT #{limit}
      """)
  List<Row> find(@Param("ownerUserId") String ownerUserId, @Param("cursorOccurredOn") LocalDate cursorOccurredOn,
                 @Param("cursorTransactionId") String cursorTransactionId, @Param("limit") int limit,
                 @Param("accountId") String accountId, @Param("instrumentId") String instrumentId,
                 @Param("transactionType") String transactionType, @Param("strategyKey") String strategyKey,
                 @Param("search") String search,
                 @Param("from") LocalDate from, @Param("to") LocalDate to);

  record Row(String transactionId, String transactionType, LocalDate occurredOn, String currency, long ledgerVersion,
             String sourceType, String importExportFileId) {
  }
}
