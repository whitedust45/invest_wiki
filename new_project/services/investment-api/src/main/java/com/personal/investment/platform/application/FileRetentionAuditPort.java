package com.personal.investment.platform.application;

@FunctionalInterface
public interface FileRetentionAuditPort {
  void recordDeleted(ExpiredImportExportFile file);
}
