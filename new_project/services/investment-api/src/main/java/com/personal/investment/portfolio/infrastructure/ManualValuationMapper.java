package com.personal.investment.portfolio.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ManualValuationMapper {
  @Insert("""
      INSERT INTO portfolio_db.portfolio_manual_valuation
        (manual_valuation_id, owner_user_id, instrument_id, valuation_date, market_value_cent, unit_price_cent,
         currency, priority, valid_until, note, created_by_user_id, created_at)
      VALUES
        (#{manualValuationId}, #{ownerUserId}, #{instrumentId}, #{valuationDate}, #{marketValueCent},
         #{unitPriceCent}, #{currency}, #{priority}, #{validUntil}, #{note}, #{createdByUserId}, UTC_TIMESTAMP(3))
      """)
  int insert(InsertRow row);

  record InsertRow(String manualValuationId, String ownerUserId, String instrumentId, LocalDate valuationDate,
                   Long marketValueCent, Long unitPriceCent, String currency, short priority, Instant validUntil,
                   String note, String createdByUserId) {
  }
}
