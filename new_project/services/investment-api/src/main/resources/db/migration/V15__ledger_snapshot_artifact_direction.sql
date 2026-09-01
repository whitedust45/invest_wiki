ALTER TABLE platform_db.import_export_file
  DROP CHECK ck_import_export_file_direction,
  ADD CONSTRAINT ck_import_export_file_direction
    CHECK (direction IN ('IMPORT','RECONCILIATION_EVIDENCE','SNAPSHOT'));
