ALTER TABLE platform_db.import_preview
  ADD KEY idx_import_preview_file_status (owner_user_id, import_export_file_id, status);
