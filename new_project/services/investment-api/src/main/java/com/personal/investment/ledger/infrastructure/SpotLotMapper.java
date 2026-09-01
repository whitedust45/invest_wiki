package com.personal.investment.ledger.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SpotLotMapper {
  @Select("""
      SELECT position_id AS positionId
      FROM portfolio_db.portfolio_position
      WHERE owner_user_id = #{ownerUserId} AND account_id = #{cashAccountId} AND instrument_id = #{instrumentId}
      LIMIT 1
      """)
  String findPositionId(@Param("ownerUserId") String ownerUserId, @Param("cashAccountId") String cashAccountId,
      @Param("instrumentId") String instrumentId);

  @Select("""
      SELECT lot.source_trade_detail_id AS sourceTradeDetailId, tx.transaction_id AS sourceTransactionId,
             detail.detail_no AS detailNo, lot.opened_on AS openedOn, lot.opened_quantity AS openedQuantity,
             lot.remaining_quantity AS remainingQuantity, lot.opened_cost_cent AS openedCostCent,
             lot.remaining_cost_cent AS remainingCostCent
      FROM portfolio_db.portfolio_position_lot lot
      INNER JOIN ledger_db.ledger_trade_detail detail ON detail.trade_detail_id = lot.source_trade_detail_id
      INNER JOIN ledger_db.ledger_transaction tx ON tx.transaction_id = detail.transaction_id
      INNER JOIN portfolio_db.portfolio_position position ON position.position_id = lot.position_id
      WHERE position.owner_user_id = #{ownerUserId}
        AND position.account_id = #{cashAccountId}
        AND position.instrument_id = #{instrumentId}
      ORDER BY tx.occurred_on, tx.transaction_id, detail.detail_no
      """)
  List<LotRow> findLots(@Param("ownerUserId") String ownerUserId, @Param("cashAccountId") String cashAccountId,
      @Param("instrumentId") String instrumentId);

  @Select("""
      SELECT source_trade_detail_id AS sourceTradeDetailId, position_lot_id AS positionLotId
      FROM portfolio_db.portfolio_position_lot
      WHERE position_id = #{positionId}
      """)
  List<LotIdRow> findLotIds(@Param("positionId") String positionId);

  @Insert("""
      INSERT INTO portfolio_db.portfolio_position
        (position_id, owner_user_id, account_id, instrument_id, quantity, average_cost_cent, currency,
         source_ledger_version, projection_version, calculated_at, created_at)
      VALUES
        (#{positionId}, #{ownerUserId}, #{cashAccountId}, #{instrumentId}, #{quantity}, NULL, #{currency},
         #{sourceLedgerVersion}, #{sourceLedgerVersion}, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
      """)
  int insertPosition(PositionRow position);

  @Update("""
      UPDATE portfolio_db.portfolio_position
      SET quantity = #{quantity}, average_cost_cent = NULL, currency = #{currency},
          source_ledger_version = #{sourceLedgerVersion}, projection_version = #{sourceLedgerVersion},
          calculated_at = UTC_TIMESTAMP(3)
      WHERE position_id = #{positionId}
      """)
  int updatePosition(PositionUpdateRow position);

  @Delete("DELETE FROM portfolio_db.portfolio_position_lot WHERE position_id = #{positionId}")
  int deleteLots(@Param("positionId") String positionId);

  @Delete("""
      DELETE lot FROM portfolio_db.portfolio_position_lot lot
      INNER JOIN portfolio_db.portfolio_position position ON position.position_id = lot.position_id
      WHERE position.owner_user_id = #{ownerUserId}
      """)
  int deleteLotsByOwner(@Param("ownerUserId") String ownerUserId);

  @Delete("DELETE FROM portfolio_db.portfolio_position WHERE owner_user_id = #{ownerUserId}")
  int deletePositionsByOwner(@Param("ownerUserId") String ownerUserId);

  @Insert("""
      INSERT INTO portfolio_db.portfolio_position_lot
        (position_lot_id, position_id, source_trade_detail_id, lot_no, opened_on, opened_quantity,
         remaining_quantity, unit_cost_cent, currency, source_ledger_version, opened_cost_cent,
         remaining_cost_cent, calculated_at, created_at)
      VALUES
        (#{positionLotId}, #{positionId}, #{sourceTradeDetailId}, #{lotNo}, #{openedOn}, #{openedQuantity},
         #{remainingQuantity}, 0, #{currency}, #{sourceLedgerVersion}, #{openedCostCent}, #{remainingCostCent},
         UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
      """)
  int insertLot(LotInsertRow lot);

  record LotRow(String sourceTradeDetailId, String sourceTransactionId, int detailNo, LocalDate openedOn,
                BigDecimal openedQuantity, BigDecimal remainingQuantity, long openedCostCent,
                long remainingCostCent) {
  }

  record LotIdRow(String sourceTradeDetailId, String positionLotId) {
  }

  record PositionRow(String positionId, String ownerUserId, String cashAccountId, String instrumentId,
                     BigDecimal quantity, String currency, long sourceLedgerVersion) {
  }

  record PositionUpdateRow(String positionId, BigDecimal quantity, String currency, long sourceLedgerVersion) {
  }

  record LotInsertRow(String positionLotId, String positionId, String sourceTradeDetailId, int lotNo,
                      LocalDate openedOn, BigDecimal openedQuantity, BigDecimal remainingQuantity,
                      String currency, long sourceLedgerVersion, long openedCostCent, long remainingCostCent) {
  }
}
