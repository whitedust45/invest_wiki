package com.personal.investment.portfolio.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PortfolioOverviewMapper {
  @Select("""
      SELECT account.account_id AS accountId, account.account_code AS accountCode, account.account_kind AS accountKind,
             account.currency AS currency,
             COALESCE(SUM(CASE WHEN tx.transaction_id IS NULL THEN 0
               WHEN posting.posting_side = 'DEBIT' THEN posting.amount_cent ELSE -posting.amount_cent END), 0)
               AS balanceCent
      FROM ledger_db.ledger_account account
      LEFT JOIN ledger_db.ledger_posting posting ON posting.account_id = account.account_id
      LEFT JOIN ledger_db.ledger_transaction tx ON tx.transaction_id = posting.transaction_id
        AND tx.owner_user_id = account.owner_user_id AND tx.occurred_on <= #{asOf}
      WHERE account.owner_user_id = #{ownerUserId}
        AND account.account_kind IN ('ASSET_CASH', 'ASSET_MARGIN')
      GROUP BY account.account_id, account.account_code, account.account_kind, account.currency
      ORDER BY account.account_kind, account.account_id
      """)
  List<AccountBalanceRow> findAccountBalances(@Param("ownerUserId") String ownerUserId, @Param("asOf") LocalDate asOf);

  @Select("""
      SELECT account_id AS accountId, currency
      FROM ledger_db.ledger_account
      WHERE owner_user_id = #{ownerUserId} AND account_kind = 'ASSET_CASH'
      ORDER BY account_id
      """)
  List<CashAccountRow> findCashAccounts(@Param("ownerUserId") String ownerUserId);

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
  List<FuturesPositionRow> findFuturesPositions(@Param("ownerUserId") String ownerUserId,
                                                 @Param("cashAccountId") String cashAccountId,
                                                 @Param("asOf") LocalDate asOf);

  @Select("""
      SELECT instrument_id AS instrumentId, currency, valuation_date AS valuationDate,
             unit_price_cent AS unitPriceCent, market_value_cent AS marketValueCent, priority,
             valid_until AS validUntil, created_at AS createdAt
      FROM portfolio_db.portfolio_manual_valuation
      WHERE owner_user_id = #{ownerUserId} AND valuation_date <= #{asOf}
      ORDER BY instrument_id, priority DESC, valuation_date DESC, created_at DESC
      """)
  List<ManualValuationRow> findManualValuations(@Param("ownerUserId") String ownerUserId,
                                                 @Param("asOf") LocalDate asOf);

  @Select("""
      SELECT COALESCE((SELECT ledger_version FROM ledger_db.ledger_state WHERE owner_user_id = #{ownerUserId}), 0)
      """)
  long currentLedgerVersion(@Param("ownerUserId") String ownerUserId);

  record AccountBalanceRow(String accountId, String accountCode, String accountKind, String currency, long balanceCent) {
  }

  record CashAccountRow(String accountId, String currency) {
  }

  record FuturesPositionRow(String instrumentId, BigDecimal quantity) {
  }

  record ManualValuationRow(String instrumentId, String currency, LocalDate valuationDate, Long unitPriceCent,
                            Long marketValueCent, short priority, Instant validUntil, LocalDateTime createdAt) {
  }
}
