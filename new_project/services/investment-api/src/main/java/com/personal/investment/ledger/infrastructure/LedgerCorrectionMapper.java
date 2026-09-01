package com.personal.investment.ledger.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LedgerCorrectionMapper {
  @Select("""
      SELECT transaction_id AS transactionId, owner_user_id AS ownerUserId, transaction_type AS transactionType,
             strategy_key AS strategyKey, operation_group_key AS operationGroupKey,
             occurred_on AS occurredOn, source_type AS sourceType, import_export_file_id AS importExportFileId,
             correction_root_transaction_id AS correctionRootTransactionId,
             reversal_of_transaction_id AS reversalOfTransactionId, revision_no AS revisionNo,
             ledger_version AS ledgerVersion, note
      FROM ledger_db.ledger_transaction
      WHERE owner_user_id = #{ownerUserId} AND transaction_id = #{transactionId}
      """)
  TransactionRow findTransaction(@Param("ownerUserId") String ownerUserId, @Param("transactionId") String transactionId);

  @Select("""
      SELECT posting_id AS postingId, account_id AS accountId, posting_no AS postingNo,
             posting_side AS postingSide, amount_cent AS amountCent, currency
      FROM ledger_db.ledger_posting
      WHERE transaction_id = #{transactionId}
      ORDER BY posting_no
      """)
  List<PostingRow> findPostings(@Param("transactionId") String transactionId);

  @Select("""
      SELECT trade_detail_id AS tradeDetailId, detail_no AS detailNo, instrument_id AS instrumentId,
             position_effect AS positionEffect, quantity, unit_price_cent AS unitPriceCent,
             price_points AS pricePoints, contract_multiplier_cent AS contractMultiplierCent,
             delivery_date AS deliveryDate, fee_cent AS feeCent, option_contract_multiplier AS optionContractMultiplier
      FROM ledger_db.ledger_trade_detail
      WHERE transaction_id = #{transactionId}
      ORDER BY detail_no
      """)
  List<TradeDetailRow> findTradeDetails(@Param("transactionId") String transactionId);

  @Select("""
      SELECT corporate_action_id AS corporateActionId, transaction_id AS transactionId, instrument_id AS instrumentId,
             action_type AS actionType, effective_on AS effectiveOn, ratio_numerator AS ratioNumerator,
             ratio_denominator AS ratioDenominator
      FROM ledger_db.ledger_corporate_action
      WHERE transaction_id = #{transactionId}
      """)
  CorporateActionRow findCorporateAction(@Param("transactionId") String transactionId);

  @Select("""
      SELECT income_detail_id AS incomeDetailId, income_type AS incomeType, instrument_id AS instrumentId,
             entitlement_date AS entitlementDate, gross_amount_cent AS grossAmountCent,
             tax_withheld_cent AS taxWithheldCent, per_share_amount_cent AS perShareAmountCent, currency
      FROM ledger_db.ledger_income_detail
      WHERE transaction_id = #{transactionId}
      """)
  IncomeRow findIncome(@Param("transactionId") String transactionId);

  @Select("""
      SELECT COUNT(*)
      FROM ledger_db.ledger_transaction
      WHERE owner_user_id = #{ownerUserId} AND reversal_of_transaction_id = #{transactionId}
        AND transaction_type = 'REVERSAL'
      """)
  int countDirectReversal(@Param("ownerUserId") String ownerUserId, @Param("transactionId") String transactionId);

  @Select("""
      SELECT COALESCE(MAX(revision_no), -1) + 1
      FROM ledger_db.ledger_transaction
      WHERE owner_user_id = #{ownerUserId} AND correction_root_transaction_id = #{correctionRootTransactionId}
      """)
  int nextRevisionNo(@Param("ownerUserId") String ownerUserId,
      @Param("correctionRootTransactionId") String correctionRootTransactionId);

  record TransactionRow(String transactionId, String ownerUserId, String transactionType, String strategyKey,
                        String operationGroupKey,
                        LocalDate occurredOn,
                        String sourceType, String importExportFileId, String correctionRootTransactionId, String reversalOfTransactionId,
                        int revisionNo, long ledgerVersion, String note) {
  }

  record PostingRow(String postingId, String accountId, int postingNo, String postingSide, long amountCent,
                    String currency) {
  }

  record TradeDetailRow(String tradeDetailId, int detailNo, String instrumentId, String positionEffect,
                        BigDecimal quantity, Long unitPriceCent, BigDecimal pricePoints, Long contractMultiplierCent,
                        LocalDate deliveryDate, long feeCent, Long optionContractMultiplier) {
  }

  record CorporateActionRow(String corporateActionId, String transactionId, String instrumentId, String actionType,
                            LocalDate effectiveOn, long ratioNumerator, long ratioDenominator) {
  }

  record IncomeRow(String incomeDetailId, String incomeType, String instrumentId, LocalDate entitlementDate,
                   long grossAmountCent, long taxWithheldCent, Long perShareAmountCent, String currency) {
  }
}
