-- V1 已在本地开发环境应用；追踪 ID 可为 UUID（36 位）或上游传入的最长 128 位值。
ALTER TABLE identity_db.iam_login_audit
  MODIFY trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL;

ALTER TABLE platform_db.audit_log
  MODIFY trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;
