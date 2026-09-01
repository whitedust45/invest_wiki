package com.personal.investment.ledger.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FuturesHistoryMapper {
  @Select("""
      SELECT tx.transaction_id AS transactionId, tx.transaction_type AS transactionType, tx.occurred_on AS occurredOn,
             cash.account_id AS cashAccountId, locked.account_id AS lockedMarginAccountId,
             detail.instrument_id AS instrumentId, detail.trade_detail_id AS tradeDetailId, detail.detail_no AS detailNo,
             detail.quantity AS quantity, detail.price_points AS pricePoints,
             detail.contract_multiplier_cent AS contractMultiplierCent,
             COALESCE((
               SELECT locked_posting.amount_cent
               FROM ledger_db.ledger_posting locked_posting
               WHERE locked_posting.transaction_id = tx.transaction_id
                 AND locked_posting.account_id = locked.account_id
                 AND locked_posting.posting_side = 'DEBIT'
               LIMIT 1
             ), 0) AS initialMarginCent,
             cash.currency
      FROM ledger_db.ledger_transaction tx
      INNER JOIN ledger_db.ledger_trade_detail detail ON detail.transaction_id = tx.transaction_id
      INNER JOIN ledger_db.ledger_account cash ON cash.account_id = (
        SELECT SUBSTRING_INDEX(margin.account_code, ':', -1)
        FROM ledger_db.ledger_posting margin_posting
        INNER JOIN ledger_db.ledger_account margin ON margin.account_id = margin_posting.account_id
        WHERE margin_posting.transaction_id = tx.transaction_id
          AND margin.owner_user_id = #{ownerUserId}
          AND margin.account_kind = 'ASSET_MARGIN'
          AND (margin.account_code LIKE 'MRGAV:%' OR margin.account_code LIKE 'MRGLK:%')
        ORDER BY CASE WHEN margin.account_code LIKE 'MRGLK:%' THEN 0 ELSE 1 END
        LIMIT 1
      )
      INNER JOIN ledger_db.ledger_account locked ON locked.owner_user_id = #{ownerUserId}
        AND locked.account_kind = 'ASSET_MARGIN'
        AND locked.account_code = CONCAT('MRGLK:', cash.account_id)
      WHERE tx.owner_user_id = #{ownerUserId}
        AND tx.transaction_type IN ('FUTURES_OPEN', 'FUTURES_CLOSE', 'FUTURES_DAILY_SETTLEMENT')
        AND NOT EXISTS (
          SELECT 1 FROM ledger_db.ledger_transaction reversal
          WHERE reversal.reversal_of_transaction_id = tx.transaction_id
            AND reversal.transaction_type = 'REVERSAL'
        )
      ORDER BY tx.occurred_on, tx.transaction_id, detail.detail_no
      """)
  List<Row> findAllByOwner(@Param("ownerUserId") String ownerUserId);

  record Row(String transactionId, String transactionType, LocalDate occurredOn, String cashAccountId,
             String lockedMarginAccountId, String instrumentId, String tradeDetailId, int detailNo,
             BigDecimal quantity, BigDecimal pricePoints, long contractMultiplierCent, long initialMarginCent,
             String currency) {
  }
}
