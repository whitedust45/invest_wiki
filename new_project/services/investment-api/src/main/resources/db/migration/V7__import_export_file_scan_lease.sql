ALTER TABLE platform_db.import_export_file
  ADD COLUMN scan_lease_until DATETIME(3) NULL AFTER expires_at,
  ADD KEY idx_import_export_scan_queue (status, scan_lease_until, created_at);
