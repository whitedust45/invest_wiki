package com.personal.investment.ledger.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SpotHistoryMapper {
  @Select("""
      SELECT tx.transaction_id AS transactionId, tx.transaction_type AS transactionType, tx.occurred_on AS occurredOn,
             COALESCE(cash.account_id, mapped_cash.account_id) AS cashAccountId, detail.instrument_id AS instrumentId,
             detail.trade_detail_id AS tradeDetailId, detail.detail_no AS detailNo, detail.quantity AS quantity,
             detail.unit_price_cent AS unitPriceCent, detail.fee_cent AS feeCent,
             detail.option_contract_multiplier AS optionContractMultiplier,
             COALESCE(cash.currency, mapped_cash.currency) AS currency
      FROM ledger_db.ledger_transaction tx
      INNER JOIN ledger_db.ledger_trade_detail detail ON detail.transaction_id = tx.transaction_id
      LEFT JOIN ledger_db.ledger_account cash ON cash.owner_user_id = #{ownerUserId}
        AND cash.account_kind = 'ASSET_CASH'
        AND EXISTS (
          SELECT 1 FROM ledger_db.ledger_posting cash_posting
          WHERE cash_posting.transaction_id = tx.transaction_id AND cash_posting.account_id = cash.account_id
        )
      LEFT JOIN ledger_db.ledger_account investment ON investment.owner_user_id = #{ownerUserId}
        AND investment.account_kind = 'ASSET_INVESTMENT'
        AND EXISTS (
          SELECT 1 FROM ledger_db.ledger_posting investment_posting
          WHERE investment_posting.transaction_id = tx.transaction_id
            AND investment_posting.account_id = investment.account_id
        )
      LEFT JOIN ledger_db.ledger_account mapped_cash ON mapped_cash.account_id =
        SUBSTRING_INDEX(SUBSTRING_INDEX(investment.account_code, ':', 2), ':', -1)
        AND mapped_cash.owner_user_id = #{ownerUserId} AND mapped_cash.account_kind = 'ASSET_CASH'
      WHERE tx.owner_user_id = #{ownerUserId}
        AND tx.transaction_type IN ('TRADE_BUY', 'TRADE_SELL', 'OPTION_OPEN', 'OPTION_CLOSE', 'OPTION_EXPIRE')
        AND COALESCE(cash.account_id, mapped_cash.account_id) IS NOT NULL
        AND NOT EXISTS (
          SELECT 1 FROM ledger_db.ledger_transaction reversal
          WHERE reversal.reversal_of_transaction_id = tx.transaction_id
            AND reversal.transaction_type = 'REVERSAL')
      ORDER BY tx.occurred_on, tx.transaction_id, detail.detail_no
      """)
  List<Row> findAllByOwner(@Param("ownerUserId") String ownerUserId);

  @Select("""
      SELECT tx.transaction_id AS transactionId, action.effective_on AS effectiveOn,
             action.instrument_id AS instrumentId, action.action_type AS actionType,
             action.ratio_numerator AS ratioNumerator, action.ratio_denominator AS ratioDenominator
      FROM ledger_db.ledger_corporate_action action
      INNER JOIN ledger_db.ledger_transaction tx ON tx.transaction_id = action.transaction_id
      WHERE tx.owner_user_id = #{ownerUserId}
        AND tx.transaction_type = 'CORPORATE_ACTION'
        AND NOT EXISTS (
          SELECT 1 FROM ledger_db.ledger_transaction reversal
          WHERE reversal.reversal_of_transaction_id = tx.transaction_id
            AND reversal.transaction_type = 'REVERSAL')
      ORDER BY action.effective_on, tx.transaction_id
      """)
  List<CorporateActionRow> findCorporateActionsByOwner(@Param("ownerUserId") String ownerUserId);

  record Row(String transactionId, String transactionType, LocalDate occurredOn, String cashAccountId,
             String instrumentId, String tradeDetailId, int detailNo, BigDecimal quantity, Long unitPriceCent,
             long feeCent, Long optionContractMultiplier, String currency) {
  }

  record CorporateActionRow(String transactionId, LocalDate effectiveOn, String instrumentId, String actionType,
                            long ratioNumerator, long ratioDenominator) {
  }
}
