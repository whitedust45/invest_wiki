package com.personal.investment.ledger.infrastructure;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LedgerAccountMapper {
  @Insert("""
      INSERT INTO ledger_db.ledger_account
        (account_id, owner_user_id, account_code, account_kind, currency, display_name, status,
         created_at, updated_at, version)
      VALUES
        (#{accountId}, #{ownerUserId}, #{accountCode}, #{accountKind}, #{currency}, #{displayName},
         #{status}, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), #{version})
      """)
  int insert(AccountRow account);

  @Insert("""
      INSERT INTO ledger_db.ledger_account
        (account_id, owner_user_id, account_code, account_kind, currency, display_name, status,
         created_at, updated_at, version)
      VALUES
        (#{accountId}, #{ownerUserId}, #{accountCode}, #{accountKind}, #{currency}, #{displayName},
         #{status}, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), #{version})
      ON DUPLICATE KEY UPDATE account_id = account_id
      """)
  int insertSystemIfAbsent(AccountRow account);

  @Select("""
      SELECT account_id AS accountId, owner_user_id AS ownerUserId, account_code AS accountCode,
             account_kind AS accountKind, currency, display_name AS displayName, status, version
      FROM ledger_db.ledger_account
      WHERE owner_user_id = #{ownerUserId}
        AND account_kind = 'ASSET_CASH'
      ORDER BY created_at ASC, account_id ASC
      """)
  List<AccountRow> findCashAccountsByOwner(String ownerUserId);

  @Select("""
      SELECT account_id AS accountId, owner_user_id AS ownerUserId, account_code AS accountCode,
             account_kind AS accountKind, currency, display_name AS displayName, status, version
      FROM ledger_db.ledger_account
      WHERE owner_user_id = #{ownerUserId}
      ORDER BY account_code, account_id
      """)
  List<AccountRow> findAllAccountsByOwner(String ownerUserId);

  @Select("""
      SELECT EXISTS(
        SELECT 1 FROM ledger_db.ledger_account WHERE owner_user_id = #{ownerUserId})
      """)
  boolean hasAnyAccountByOwner(@Param("ownerUserId") String ownerUserId);

  @Select("""
      SELECT account_id AS accountId, owner_user_id AS ownerUserId, account_code AS accountCode,
             account_kind AS accountKind, currency, display_name AS displayName, status, version
      FROM ledger_db.ledger_account
      WHERE account_id = #{accountId}
        AND owner_user_id = #{ownerUserId}
      LIMIT 1
      """)
  AccountRow findByIdAndOwner(@Param("accountId") String accountId,
      @Param("ownerUserId") String ownerUserId);

  @Select("""
      SELECT account_id AS accountId, owner_user_id AS ownerUserId, account_code AS accountCode,
             account_kind AS accountKind, currency, display_name AS displayName, status, version
      FROM ledger_db.ledger_account
      WHERE owner_user_id = #{ownerUserId}
        AND account_code = #{accountCode}
      LIMIT 1
      """)
  AccountRow findByOwnerAndCode(@Param("ownerUserId") String ownerUserId,
      @Param("accountCode") String accountCode);

  @Select("""
      SELECT EXISTS(
        SELECT 1 FROM portfolio_db.portfolio_position
        WHERE owner_user_id = #{ownerUserId} AND account_id = #{cashAccountId} AND quantity > 0)
      """)
  boolean hasOpenSpotPosition(@Param("ownerUserId") String ownerUserId,
      @Param("cashAccountId") String cashAccountId);

  @Select("""
      SELECT EXISTS(
        SELECT 1
        FROM portfolio_db.futures_position position
        INNER JOIN ledger_db.ledger_account margin_account
          ON margin_account.account_id = position.locked_margin_account_id
        WHERE position.owner_user_id = #{ownerUserId}
          AND position.open_quantity > 0
          AND margin_account.owner_user_id = #{ownerUserId}
          AND margin_account.account_code = CONCAT('MRGLK:', #{cashAccountId}))
      """)
  boolean hasOpenFuturesPosition(@Param("ownerUserId") String ownerUserId,
      @Param("cashAccountId") String cashAccountId);

  @Select("""
      SELECT EXISTS(
        SELECT 1 FROM platform_db.async_job
        WHERE owner_user_id = #{ownerUserId}
          AND status IN ('PENDING', 'RUNNING')
          AND JSON_SEARCH(payload_json, 'one', #{cashAccountId}) IS NOT NULL)
      """)
  boolean hasActiveImportReferencingCashAccount(@Param("ownerUserId") String ownerUserId,
      @Param("cashAccountId") String cashAccountId);

  @Update("""
      UPDATE ledger_db.ledger_account
      SET status = 'DISABLED', version = version + 1, updated_at = UTC_TIMESTAMP(3)
      WHERE owner_user_id = #{ownerUserId} AND account_id = #{cashAccountId}
        AND account_kind = 'ASSET_CASH' AND status = 'ACTIVE' AND version = #{expectedVersion}
      """)
  int disableIfCurrentVersion(@Param("ownerUserId") String ownerUserId,
      @Param("cashAccountId") String cashAccountId, @Param("expectedVersion") long expectedVersion);

  record AccountRow(String accountId, String ownerUserId, String accountCode, String accountKind,
                    String currency, String displayName, String status, long version) {
  }
}
