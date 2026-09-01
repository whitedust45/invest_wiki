package com.personal.investment.platform.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditOutboxMapper {
  @Insert("""
      INSERT INTO platform_db.audit_log
        (audit_log_id, actor_user_id, resource_type, resource_reference, action, result, trace_id, detail_json, created_at)
      VALUES
        (#{auditLogId}, #{actorUserId}, #{resourceType}, #{resourceReference}, #{action}, 'SUCCESS', #{traceId},
         CAST(#{detailJson} AS JSON), UTC_TIMESTAMP(3))
      """)
  int insertAudit(AuditRow row);

  @Insert("""
      INSERT INTO platform_db.outbox_event
        (outbox_event_id, event_subject_type, event_subject_reference, event_type, event_version, payload_json,
         status, occurred_at, created_at)
      VALUES
        (#{outboxEventId}, #{subjectType}, #{subjectReference}, #{eventType}, 1, CAST(#{payloadJson} AS JSON),
         'PENDING', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
      """)
  int insertOutbox(OutboxRow row);

  record AuditRow(String auditLogId, String actorUserId, String resourceType, String resourceReference,
                  String action, String traceId, String detailJson) {
  }

  record OutboxRow(String outboxEventId, String subjectType, String subjectReference, String eventType,
                   String payloadJson) {
  }
}
