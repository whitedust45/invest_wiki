package com.personal.investment.ledger.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IncomeDetailMapper {
  @Insert("""
      INSERT INTO ledger_db.ledger_income_detail
        (income_detail_id, transaction_id, income_type, instrument_id, entitlement_date, gross_amount_cent,
         tax_withheld_cent, per_share_amount_cent, currency, created_at)
      VALUES
        (#{incomeDetailId}, #{transactionId}, #{incomeType}, #{instrumentId}, #{entitlementDate}, #{grossAmountCent},
         #{taxWithheldCent}, #{perShareAmountCent}, #{currency}, UTC_TIMESTAMP(3))
      """)
  int insert(IncomeDetailRow detail);

  record IncomeDetailRow(String incomeDetailId, String transactionId, String incomeType, String instrumentId,
                         java.time.LocalDate entitlementDate, long grossAmountCent, long taxWithheldCent,
                         Long perShareAmountCent, String currency) {
  }
}
