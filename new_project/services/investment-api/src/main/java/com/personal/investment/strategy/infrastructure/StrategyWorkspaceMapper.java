package com.personal.investment.strategy.infrastructure;

import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StrategyWorkspaceMapper {
  @Select("""
      SELECT strategy_active_rule_id AS strategyActiveRuleId, owner_user_id AS ownerUserId,
             strategy_key AS strategyKey, strategy_rule_version_id AS strategyRuleVersionId,
             binding_version AS bindingVersion
      FROM strategy_db.strategy_active_rule
      WHERE owner_user_id = #{ownerUserId} AND strategy_key = #{strategyKey}
      FOR UPDATE
      """)
  ActiveRuleRow lockActiveRule(@Param("ownerUserId") String ownerUserId, @Param("strategyKey") String strategyKey);

  @Select("""
      SELECT strategy_active_rule_id AS strategyActiveRuleId, owner_user_id AS ownerUserId,
             strategy_key AS strategyKey, strategy_rule_version_id AS strategyRuleVersionId,
             binding_version AS bindingVersion
      FROM strategy_db.strategy_active_rule
      WHERE owner_user_id = #{ownerUserId} AND strategy_key = #{strategyKey}
      """)
  ActiveRuleRow findActiveRule(@Param("ownerUserId") String ownerUserId, @Param("strategyKey") String strategyKey);

  @Select("""
      SELECT strategy_rule_version_id AS strategyRuleVersionId, owner_user_id AS ownerUserId,
             strategy_key AS strategyKey, rule_version AS ruleVersion, rule_hash AS ruleHash,
             CAST(rule_json AS CHAR) AS ruleJson, status, created_by_user_id AS createdByUserId, created_at AS createdAt
      FROM strategy_db.strategy_rule_version
      WHERE owner_user_id = #{ownerUserId} AND strategy_rule_version_id = #{strategyRuleVersionId}
      LIMIT 1
      """)
  RuleVersionRow findRuleVersion(@Param("ownerUserId") String ownerUserId,
      @Param("strategyRuleVersionId") String strategyRuleVersionId);

  @Select("""
      SELECT strategy_active_rule_id AS strategyActiveRuleId, owner_user_id AS ownerUserId,
             strategy_key AS strategyKey, strategy_rule_version_id AS strategyRuleVersionId,
             binding_version AS bindingVersion
      FROM strategy_db.strategy_active_rule
      ORDER BY owner_user_id, strategy_key
      """)
  java.util.List<ActiveRuleRow> findAllActiveRules();

  @Select("""
      SELECT e.strategy_evaluation_id AS strategyEvaluationId, e.owner_user_id AS ownerUserId,
             r.strategy_key AS strategyKey, e.strategy_rule_version_id AS strategyRuleVersionId,
             e.input_version AS inputVersion, e.as_of_at AS asOfAt, e.status, CAST(e.result_json AS CHAR) AS resultJson
      FROM strategy_db.strategy_evaluation e
      INNER JOIN strategy_db.strategy_rule_version r ON r.strategy_rule_version_id = e.strategy_rule_version_id
      WHERE e.owner_user_id = #{ownerUserId} AND r.strategy_key = #{strategyKey}
      ORDER BY e.as_of_at DESC, e.strategy_evaluation_id DESC
      LIMIT 1
      """)
  EvaluationRow findLatestEvaluation(@Param("ownerUserId") String ownerUserId, @Param("strategyKey") String strategyKey);

  @Select("""
      SELECT strategy_rule_version_id AS strategyRuleVersionId, owner_user_id AS ownerUserId,
             strategy_key AS strategyKey, rule_version AS ruleVersion, rule_hash AS ruleHash,
             CAST(rule_json AS CHAR) AS ruleJson, status, created_by_user_id AS createdByUserId, created_at AS createdAt
      FROM strategy_db.strategy_rule_version
      WHERE owner_user_id = #{ownerUserId} AND strategy_key = #{strategyKey}
        AND (#{cursorTimestamp} IS NULL OR created_at < #{cursorTimestamp}
          OR (created_at = #{cursorTimestamp} AND strategy_rule_version_id < #{cursorItemId}))
      ORDER BY created_at DESC, strategy_rule_version_id DESC
      LIMIT #{limit}
      """)
  java.util.List<RuleVersionRow> findRuleVersions(@Param("ownerUserId") String ownerUserId,
      @Param("strategyKey") String strategyKey, @Param("cursorTimestamp") Instant cursorTimestamp,
      @Param("cursorItemId") String cursorItemId, @Param("limit") int limit);

  @Select("""
      SELECT strategy_reference_nav_id AS strategyReferenceNavId, owner_user_id AS ownerUserId,
             strategy_key AS strategyKey, currency, reference_nav_cent AS referenceNavCent, as_of_at AS asOfAt,
             valid_until AS validUntil, source, created_at AS createdAt
      FROM strategy_db.strategy_reference_nav
      WHERE owner_user_id = #{ownerUserId} AND strategy_key = #{strategyKey}
        AND (#{cursorTimestamp} IS NULL OR created_at < #{cursorTimestamp}
          OR (created_at = #{cursorTimestamp} AND strategy_reference_nav_id < #{cursorItemId}))
      ORDER BY created_at DESC, strategy_reference_nav_id DESC
      LIMIT #{limit}
      """)
  java.util.List<ReferenceNavRow> findReferenceNavs(@Param("ownerUserId") String ownerUserId,
      @Param("strategyKey") String strategyKey, @Param("cursorTimestamp") Instant cursorTimestamp,
      @Param("cursorItemId") String cursorItemId, @Param("limit") int limit);

  @Select("""
      SELECT e.strategy_evaluation_id AS strategyEvaluationId, e.owner_user_id AS ownerUserId,
             r.strategy_key AS strategyKey, e.strategy_rule_version_id AS strategyRuleVersionId,
             e.input_version AS inputVersion, e.as_of_at AS asOfAt, e.status,
             CAST(e.result_json AS CHAR) AS resultJson
      FROM strategy_db.strategy_evaluation e
      INNER JOIN strategy_db.strategy_rule_version r ON r.strategy_rule_version_id = e.strategy_rule_version_id
      WHERE e.owner_user_id = #{ownerUserId} AND r.strategy_key = #{strategyKey}
        AND (#{cursorTimestamp} IS NULL OR e.as_of_at < #{cursorTimestamp}
          OR (e.as_of_at = #{cursorTimestamp} AND e.strategy_evaluation_id < #{cursorItemId}))
      ORDER BY e.as_of_at DESC, e.strategy_evaluation_id DESC
      LIMIT #{limit}
      """)
  java.util.List<EvaluationRow> findEvaluations(@Param("ownerUserId") String ownerUserId,
      @Param("strategyKey") String strategyKey, @Param("cursorTimestamp") Instant cursorTimestamp,
      @Param("cursorItemId") String cursorItemId, @Param("limit") int limit);

  @Insert("""
      INSERT INTO strategy_db.strategy_rule_version
        (strategy_rule_version_id, owner_user_id, strategy_key, rule_version, rule_hash, rule_json, status,
         created_by_user_id, created_at)
      VALUES
        (#{strategyRuleVersionId}, #{ownerUserId}, #{strategyKey}, #{ruleVersion}, #{ruleHash}, #{ruleJson},
         #{status}, #{createdByUserId}, #{createdAt})
      """)
  int insertRule(RuleVersionRow rule);

  @Insert("""
      INSERT INTO strategy_db.strategy_active_rule
        (strategy_active_rule_id, owner_user_id, strategy_key, strategy_rule_version_id, binding_version, updated_at)
      VALUES
        (#{strategyActiveRuleId}, #{ownerUserId}, #{strategyKey}, #{strategyRuleVersionId}, #{bindingVersion},
         UTC_TIMESTAMP(3))
      """)
  int insertActiveRule(ActiveRuleRow activeRule);

  @Update("""
      UPDATE strategy_db.strategy_active_rule
      SET strategy_rule_version_id = #{nextStrategyRuleVersionId}, binding_version = #{bindingVersion},
          updated_at = UTC_TIMESTAMP(3)
      WHERE owner_user_id = #{ownerUserId} AND strategy_key = #{strategyKey}
        AND strategy_rule_version_id = #{expectedStrategyRuleVersionId}
      """)
  int replaceActiveRule(@Param("ownerUserId") String ownerUserId, @Param("strategyKey") String strategyKey,
      @Param("expectedStrategyRuleVersionId") String expectedStrategyRuleVersionId,
      @Param("nextStrategyRuleVersionId") String nextStrategyRuleVersionId, @Param("bindingVersion") long bindingVersion);

  @Update("""
      UPDATE strategy_db.strategy_rule_version
      SET status = 'ARCHIVED'
      WHERE owner_user_id = #{ownerUserId} AND strategy_rule_version_id = #{strategyRuleVersionId}
        AND status = 'ACTIVE'
      """)
  int archiveRule(@Param("ownerUserId") String ownerUserId,
      @Param("strategyRuleVersionId") String strategyRuleVersionId);

  @Insert("""
      INSERT INTO strategy_db.strategy_active_rule_event
        (strategy_active_rule_event_id, owner_user_id, strategy_key, previous_strategy_rule_version_id,
         next_strategy_rule_version_id, binding_version, created_by_user_id, created_at)
      VALUES
        (#{strategyActiveRuleEventId}, #{ownerUserId}, #{strategyKey}, #{previousStrategyRuleVersionId},
         #{nextStrategyRuleVersionId}, #{bindingVersion}, #{createdByUserId}, #{createdAt})
      """)
  int insertActiveRuleEvent(ActiveRuleEventRow event);

  @Insert("""
      INSERT INTO strategy_db.strategy_reference_nav
        (strategy_reference_nav_id, owner_user_id, strategy_key, currency, reference_nav_cent, as_of_at,
         valid_until, source, created_at)
      VALUES
        (#{strategyReferenceNavId}, #{ownerUserId}, #{strategyKey}, #{currency}, #{referenceNavCent}, #{asOfAt},
         #{validUntil}, #{source}, #{createdAt})
      """)
  int insertReferenceNav(ReferenceNavRow referenceNav);

  @Select("""
      SELECT strategy_reference_nav_id AS strategyReferenceNavId, owner_user_id AS ownerUserId,
             strategy_key AS strategyKey, currency, reference_nav_cent AS referenceNavCent, as_of_at AS asOfAt,
             valid_until AS validUntil, source, created_at AS createdAt
      FROM strategy_db.strategy_reference_nav
      WHERE owner_user_id = #{ownerUserId} AND strategy_key = #{strategyKey}
        AND as_of_at <= #{asOfAt} AND valid_until >= #{asOfAt}
      ORDER BY as_of_at DESC, strategy_reference_nav_id DESC
      LIMIT 1
      """)
  ReferenceNavRow findApplicableReferenceNav(@Param("ownerUserId") String ownerUserId,
      @Param("strategyKey") String strategyKey, @Param("asOfAt") Instant asOfAt);

  @Select("""
      SELECT e.strategy_evaluation_id AS strategyEvaluationId, e.owner_user_id AS ownerUserId,
             r.strategy_key AS strategyKey, e.strategy_rule_version_id AS strategyRuleVersionId,
             e.input_version AS inputVersion, e.as_of_at AS asOfAt, e.status, CAST(e.result_json AS CHAR) AS resultJson
      FROM strategy_db.strategy_evaluation e
      INNER JOIN strategy_db.strategy_rule_version r ON r.strategy_rule_version_id = e.strategy_rule_version_id
      WHERE e.owner_user_id = #{ownerUserId} AND e.strategy_rule_version_id = #{strategyRuleVersionId}
        AND e.input_hash = #{inputHash}
      LIMIT 1
      """)
  EvaluationRow findEvaluationByInput(@Param("ownerUserId") String ownerUserId,
      @Param("strategyRuleVersionId") String strategyRuleVersionId, @Param("inputHash") byte[] inputHash);

  @Insert("""
      INSERT INTO strategy_db.strategy_evaluation
        (strategy_evaluation_id, owner_user_id, strategy_rule_version_id, input_version, input_hash, as_of_at,
         status, result_json, created_at)
      VALUES
        (#{strategyEvaluationId}, #{ownerUserId}, #{strategyRuleVersionId}, #{inputVersion}, #{inputHash}, #{asOfAt},
         #{status}, #{resultJson}, UTC_TIMESTAMP(3))
      """)
  int insertEvaluation(EvaluationInsertRow evaluation);

  @Insert("""
      INSERT INTO strategy_db.strategy_signal
        (strategy_signal_id, strategy_evaluation_id, signal_run_id, instrument_id, signal_scope, signal_key,
         signal_type, severity, rank_no, explanation, as_of_at, created_at)
      VALUES
        (#{strategySignalId}, #{strategyEvaluationId}, #{signalRunId}, #{instrumentId}, #{signalScope}, #{signalKey},
         #{signalType}, #{severity}, #{rankNo}, #{explanation}, #{asOfAt}, #{createdAt})
      """)
  int insertSignal(SignalRow signal);

  record ActiveRuleRow(String strategyActiveRuleId, String ownerUserId, String strategyKey,
                       String strategyRuleVersionId, long bindingVersion) {
  }

  record RuleVersionRow(String strategyRuleVersionId, String ownerUserId, String strategyKey, String ruleVersion,
                        byte[] ruleHash, String ruleJson, String status, String createdByUserId, Instant createdAt) {
  }

  record ActiveRuleEventRow(String strategyActiveRuleEventId, String ownerUserId, String strategyKey,
                            String previousStrategyRuleVersionId, String nextStrategyRuleVersionId,
                            long bindingVersion, String createdByUserId, Instant createdAt) {
  }

  record EvaluationRow(String strategyEvaluationId, String ownerUserId, String strategyKey,
                       String strategyRuleVersionId, String inputVersion, Instant asOfAt, String status,
                       String resultJson) {
  }

  record ReferenceNavRow(String strategyReferenceNavId, String ownerUserId, String strategyKey, String currency,
                         long referenceNavCent, Instant asOfAt, Instant validUntil, String source,
                         Instant createdAt) {
  }

  record EvaluationInsertRow(String strategyEvaluationId, String ownerUserId, String strategyRuleVersionId,
                             String inputVersion, byte[] inputHash, Instant asOfAt, String status,
                             String resultJson) {
  }

  record SignalRow(String strategySignalId, String strategyEvaluationId, String signalRunId, String instrumentId,
                   String signalScope, String signalKey, String signalType, String severity, short rankNo,
                   String explanation, Instant asOfAt, Instant createdAt) {
  }
}
