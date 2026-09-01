-- Import preview checksum verification is byte-sensitive. MySQL JSON columns normalize their textual representation,
-- so the immutable mapping and preview payloads must retain the exact serialized bytes that were hashed on creation.
ALTER TABLE platform_db.import_preview
  MODIFY COLUMN mapping_json LONGTEXT NOT NULL,
  MODIFY COLUMN preview_json LONGTEXT NOT NULL;

-- Pre-V10 uncommitted previews were persisted through JSON normalization and cannot safely be confirmed.
-- They are short-lived (24h) and users may create a fresh preview from the same scanned evidence.
UPDATE platform_db.import_preview
SET status = 'EXPIRED'
WHERE status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'NEEDS_REVIEW');
