package com.personal.investment.reporting.infrastructure;

import com.personal.investment.reporting.application.PortfolioHistoryPoint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PortfolioHistorySnapshotMapper {
  @Select("""
      SELECT EXISTS(
        SELECT 1 FROM portfolio_db.portfolio_daily_snapshot
        WHERE owner_user_id = #{ownerUserId} AND currency = #{currency} AND as_of_date = #{asOfDate}
          AND source_ledger_version = #{sourceLedgerVersion}
      )
      """)
  boolean exists(@Param("ownerUserId") String ownerUserId, @Param("currency") String currency,
                 @Param("asOfDate") LocalDate asOfDate, @Param("sourceLedgerVersion") long sourceLedgerVersion);

  @Insert("""
      INSERT INTO portfolio_db.portfolio_daily_snapshot (
        daily_snapshot_id, owner_user_id, currency, as_of_date, net_asset_cent, cash_cent, market_value_cent,
        source_ledger_version, projection_version, calculated_at, created_at
      ) VALUES (
        #{point.dailySnapshotId}, #{ownerUserId}, #{point.currency}, #{point.asOfDate}, #{point.netAssetCent},
        #{point.cashCent}, #{point.marketValueCent}, #{point.sourceLedgerVersion}, #{point.sourceLedgerVersion},
        #{point.calculatedAt}, #{point.calculatedAt}
      )
      """)
  void insert(@Param("ownerUserId") String ownerUserId, @Param("point") PortfolioHistoryPoint point);

  @Select("""
      SELECT daily_snapshot_id AS dailySnapshotId, currency, as_of_date AS asOfDate, net_asset_cent AS netAssetCent,
             cash_cent AS cashCent, market_value_cent AS marketValueCent,
             source_ledger_version AS sourceLedgerVersion, calculated_at AS calculatedAt
      FROM portfolio_db.portfolio_daily_snapshot
      WHERE owner_user_id = #{ownerUserId} AND currency = #{currency}
        AND as_of_date BETWEEN #{fromInclusive} AND #{toInclusive}
      ORDER BY as_of_date ASC, source_ledger_version ASC
      LIMIT #{limit}
      """)
  List<PortfolioHistoryPoint> list(@Param("ownerUserId") String ownerUserId, @Param("currency") String currency,
                                   @Param("fromInclusive") LocalDate fromInclusive,
                                   @Param("toInclusive") LocalDate toInclusive, @Param("limit") int limit);

  @Select("""
      SELECT owner_user_id
      FROM ledger_db.ledger_state
      WHERE ledger_version > 0
      ORDER BY owner_user_id
      """)
  List<String> ownersWithLedger();
}
