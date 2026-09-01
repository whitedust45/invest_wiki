package com.personal.investment.ledger.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FuturesPositionMapper {
  @Select("""
      SELECT lot.source_trade_detail_id AS sourceTradeDetailId, lot.opened_on AS openedOn,
             lot.opened_quantity AS openedQuantity, lot.remaining_quantity AS remainingQuantity,
             lot.open_price_points AS openPricePoints, lot.last_settlement_price_points AS lastSettlementPricePoints,
             lot.last_settlement_on AS lastSettlementOn,
             lot.contract_multiplier_cent AS contractMultiplierCent,
             lot.allocated_initial_margin_cent AS allocatedInitialMarginCent,
             lot.remaining_initial_margin_cent AS remainingInitialMarginCent, lot.currency
      FROM portfolio_db.futures_position position
      INNER JOIN portfolio_db.futures_position_lot lot ON lot.futures_position_id = position.futures_position_id
      WHERE position.owner_user_id = #{ownerUserId}
        AND position.locked_margin_account_id = #{lockedMarginAccountId}
        AND position.instrument_id = #{instrumentId}
      ORDER BY lot.opened_on, lot.source_trade_detail_id
      """)
  List<LotRow> findLots(@Param("ownerUserId") String ownerUserId,
                        @Param("lockedMarginAccountId") String lockedMarginAccountId,
                        @Param("instrumentId") String instrumentId);

  @Delete("""
      DELETE lot
      FROM portfolio_db.futures_position_lot lot
      INNER JOIN portfolio_db.futures_position position ON position.futures_position_id = lot.futures_position_id
      WHERE position.owner_user_id = #{ownerUserId}
        AND position.locked_margin_account_id = #{lockedMarginAccountId}
        AND position.instrument_id = #{instrumentId}
      """)
  int deleteLots(@Param("ownerUserId") String ownerUserId,
                 @Param("lockedMarginAccountId") String lockedMarginAccountId,
                 @Param("instrumentId") String instrumentId);

  @Delete("""
      DELETE FROM portfolio_db.futures_position
      WHERE owner_user_id = #{ownerUserId}
        AND locked_margin_account_id = #{lockedMarginAccountId}
        AND instrument_id = #{instrumentId}
      """)
  int deletePosition(@Param("ownerUserId") String ownerUserId,
                     @Param("lockedMarginAccountId") String lockedMarginAccountId,
                     @Param("instrumentId") String instrumentId);

  @Delete("""
      DELETE lot
      FROM portfolio_db.futures_position_lot lot
      INNER JOIN portfolio_db.futures_position position ON position.futures_position_id = lot.futures_position_id
      WHERE position.owner_user_id = #{ownerUserId}
      """)
  int deleteOwnerLots(@Param("ownerUserId") String ownerUserId);

  @Delete("""
      DELETE FROM portfolio_db.futures_position
      WHERE owner_user_id = #{ownerUserId}
      """)
  int deleteOwnerPositions(@Param("ownerUserId") String ownerUserId);

  @Insert("""
      INSERT INTO portfolio_db.futures_position
        (futures_position_id, owner_user_id, locked_margin_account_id, instrument_id, open_quantity,
         average_open_price_points, contract_multiplier_cent, currency, source_ledger_version, projection_version,
         calculated_at, created_at)
      VALUES
        (#{futuresPositionId}, #{ownerUserId}, #{lockedMarginAccountId}, #{instrumentId}, #{openQuantity},
         #{averageOpenPricePoints}, #{contractMultiplierCent}, #{currency}, #{sourceLedgerVersion},
         #{projectionVersion}, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
      """)
  int insertPosition(PositionRow row);

  @Insert("""
      INSERT INTO portfolio_db.futures_position_lot
        (futures_position_lot_id, futures_position_id, source_trade_detail_id, opened_on, opened_quantity,
         remaining_quantity, open_price_points, last_settlement_price_points, last_settlement_on, contract_multiplier_cent,
         allocated_initial_margin_cent, remaining_initial_margin_cent, currency, source_ledger_version,
         calculated_at, created_at)
      VALUES
        (#{futuresPositionLotId}, #{futuresPositionId}, #{sourceTradeDetailId}, #{openedOn}, #{openedQuantity},
         #{remainingQuantity}, #{openPricePoints}, #{lastSettlementPricePoints}, #{lastSettlementOn}, #{contractMultiplierCent},
         #{allocatedInitialMarginCent}, #{remainingInitialMarginCent}, #{currency}, #{sourceLedgerVersion},
         UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
      """)
  int insertLot(LotInsertRow row);

  record LotRow(String sourceTradeDetailId, LocalDate openedOn, BigDecimal openedQuantity,
                BigDecimal remainingQuantity, BigDecimal openPricePoints, BigDecimal lastSettlementPricePoints,
                LocalDate lastSettlementOn,
                long contractMultiplierCent, long allocatedInitialMarginCent, long remainingInitialMarginCent,
                String currency) {
  }

  record PositionRow(String futuresPositionId, String ownerUserId, String lockedMarginAccountId,
                     String instrumentId, BigDecimal openQuantity, BigDecimal averageOpenPricePoints,
                     long contractMultiplierCent, String currency, long sourceLedgerVersion, long projectionVersion) {
  }

  record LotInsertRow(String futuresPositionLotId, String futuresPositionId, String sourceTradeDetailId,
                      LocalDate openedOn, BigDecimal openedQuantity, BigDecimal remainingQuantity,
                      BigDecimal openPricePoints, BigDecimal lastSettlementPricePoints, LocalDate lastSettlementOn,
                      long contractMultiplierCent,
                      long allocatedInitialMarginCent, long remainingInitialMarginCent, String currency,
                      long sourceLedgerVersion) {
  }
}
