package com.personal.investment.strategy.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StrategySeedMapper {
  @Select("SELECT COUNT(*) FROM ledger_db.ledger_transaction WHERE owner_user_id = #{ownerUserId}")
  long countLedgerTransactions(@Param("ownerUserId") String ownerUserId);

  @Select("""
      SELECT COUNT(*) FROM strategy_db.strategy_seed_run
      WHERE owner_user_id = #{ownerUserId} AND seed_name = #{seedName}
      """)
  long countSeedRuns(@Param("ownerUserId") String ownerUserId, @Param("seedName") String seedName);

  @Insert("""
      INSERT INTO strategy_db.strategy_seed_run
        (strategy_seed_run_id, owner_user_id, seed_name, fixture_checksum, created_at)
      VALUES (#{strategySeedRunId}, #{ownerUserId}, #{seedName}, #{fixtureChecksum}, UTC_TIMESTAMP(3))
      """)
  int insert(@Param("strategySeedRunId") String strategySeedRunId, @Param("ownerUserId") String ownerUserId,
      @Param("seedName") String seedName, @Param("fixtureChecksum") byte[] fixtureChecksum);
}
