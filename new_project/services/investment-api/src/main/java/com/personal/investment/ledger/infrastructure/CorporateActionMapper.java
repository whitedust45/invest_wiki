package com.personal.investment.ledger.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CorporateActionMapper {
  @Insert("""
      INSERT INTO ledger_db.ledger_corporate_action
        (corporate_action_id, transaction_id, instrument_id, action_type, effective_on, ratio_numerator,
         ratio_denominator, created_at)
      VALUES
        (#{corporateActionId}, #{transactionId}, #{instrumentId}, #{actionType}, #{effectiveOn}, #{ratioNumerator},
         #{ratioDenominator}, UTC_TIMESTAMP(3))
      """)
  int insert(Row row);

  record Row(String corporateActionId, String transactionId, String instrumentId, String actionType,
             java.time.LocalDate effectiveOn, long ratioNumerator, long ratioDenominator) {
  }
}
