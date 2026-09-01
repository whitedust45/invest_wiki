package com.personal.investment.strategy.infrastructure;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StrategyScanMapper {
  @Insert("""
      INSERT INTO strategy_db.strategy_scan
        (strategy_scan_id, owner_user_id, requested_strategy_keys_json, as_of_at, status, attempt_no, started_at,
         completed_at, result_json, created_at, updated_at)
      VALUES
        (#{strategyScanId}, #{ownerUserId}, CAST(#{requestedStrategyKeysJson} AS JSON), #{asOfAt}, #{status},
         #{attemptNo}, NULL, NULL, NULL, #{createdAt}, #{createdAt})
      """)
  int insert(ScanRow scan);

  @Select("""
      SELECT strategy_scan_id AS strategyScanId, owner_user_id AS ownerUserId,
             CAST(requested_strategy_keys_json AS CHAR) AS requestedStrategyKeysJson, as_of_at AS asOfAt, status,
             attempt_no AS attemptNo, started_at AS startedAt, completed_at AS completedAt,
             CAST(result_json AS CHAR) AS resultJson, created_at AS createdAt
      FROM strategy_db.strategy_scan
      WHERE owner_user_id = #{ownerUserId} AND strategy_scan_id = #{strategyScanId}
      LIMIT 1
      """)
  ScanRow find(@Param("ownerUserId") String ownerUserId, @Param("strategyScanId") String strategyScanId);

  @Select("""
      SELECT strategy_scan_id AS strategyScanId, owner_user_id AS ownerUserId,
             CAST(requested_strategy_keys_json AS CHAR) AS requestedStrategyKeysJson, as_of_at AS asOfAt, status,
             attempt_no AS attemptNo, started_at AS startedAt, completed_at AS completedAt,
             CAST(result_json AS CHAR) AS resultJson, created_at AS createdAt
      FROM strategy_db.strategy_scan
      WHERE status = 'QUEUED' OR (status = 'RUNNING' AND started_at < #{reclaimBefore})
      ORDER BY CASE WHEN status = 'QUEUED' THEN 0 ELSE 1 END, created_at, strategy_scan_id
      LIMIT 1
      """)
  ScanRow findNextRunnable(@Param("reclaimBefore") Instant reclaimBefore);

  @Update("""
      UPDATE strategy_db.strategy_scan
      SET status = 'RUNNING', attempt_no = attempt_no + 1, started_at = #{startedAt}, updated_at = #{startedAt}
      WHERE strategy_scan_id = #{strategyScanId}
        AND (status = 'QUEUED' OR (status = 'RUNNING' AND started_at < #{reclaimBefore}))
      """)
  int claim(@Param("strategyScanId") String strategyScanId, @Param("reclaimBefore") Instant reclaimBefore,
      @Param("startedAt") Instant startedAt);

  @Insert("""
      INSERT INTO strategy_db.strategy_scan_item
        (strategy_scan_item_id, strategy_scan_id, strategy_key, strategy_evaluation_id, status, failure_code,
         failure_message, created_at)
      VALUES
        (#{strategyScanItemId}, #{strategyScanId}, #{strategyKey}, #{strategyEvaluationId}, #{status},
         #{failureCode}, #{failureMessage}, #{createdAt})
      """)
  int insertItem(ScanItemRow item);

  @Select("""
      SELECT strategy_scan_item_id AS strategyScanItemId, strategy_scan_id AS strategyScanId,
             strategy_key AS strategyKey, strategy_evaluation_id AS strategyEvaluationId, status,
             failure_code AS failureCode, failure_message AS failureMessage, created_at AS createdAt
      FROM strategy_db.strategy_scan_item
      WHERE strategy_scan_id = #{strategyScanId}
      ORDER BY created_at, strategy_scan_item_id
      """)
  List<ScanItemRow> findItems(@Param("strategyScanId") String strategyScanId);

  @Update("""
      UPDATE strategy_db.strategy_scan
      SET status = #{status}, result_json = CAST(#{resultJson} AS JSON), completed_at = #{completedAt},
          updated_at = #{completedAt}
      WHERE strategy_scan_id = #{strategyScanId} AND status = 'RUNNING'
      """)
  int complete(@Param("strategyScanId") String strategyScanId, @Param("status") String status,
      @Param("resultJson") String resultJson, @Param("completedAt") Instant completedAt);

  record ScanRow(String strategyScanId, String ownerUserId, String requestedStrategyKeysJson, Instant asOfAt,
                 String status, short attemptNo, Instant startedAt, Instant completedAt, String resultJson,
                 Instant createdAt) {
  }

  record ScanItemRow(String strategyScanItemId, String strategyScanId, String strategyKey,
                     String strategyEvaluationId, String status, String failureCode, String failureMessage,
                     Instant createdAt) {
  }
}
