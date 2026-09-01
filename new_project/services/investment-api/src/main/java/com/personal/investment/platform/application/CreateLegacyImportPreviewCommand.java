package com.personal.investment.platform.application;

import java.util.List;

/** Mapping is supplied by the user and captured verbatim with the immutable dry-run preview. */
public record CreateLegacyImportPreviewCommand(String importExportFileId, LegacyImportFormat format,
    String snapshotId, List<LegacyCurrencyMapping> currencyMappings, List<LegacyInstrumentMapping> instrumentMappings,
    List<LegacyDividendEntitlementOverride> dividendEntitlementOverrides,
    List<LegacyOptionExpiryAttestation> optionExpiryAttestations) {
  public CreateLegacyImportPreviewCommand {
    if (importExportFileId == null || importExportFileId.isBlank() || format == null) {
      throw new IllegalArgumentException("legacy import preview request is incomplete");
    }
    if (format == LegacyImportFormat.LEGACY_SQLITE && (snapshotId == null || snapshotId.isBlank())) {
      throw new IllegalArgumentException("SQLite legacy import requires snapshotId");
    }
    if (format == LegacyImportFormat.LEGACY_DASHBOARD_JSON && snapshotId != null) {
      throw new IllegalArgumentException("JSON legacy import must not provide snapshotId");
    }
    currencyMappings = List.copyOf(currencyMappings == null ? List.of() : currencyMappings);
    instrumentMappings = List.copyOf(instrumentMappings == null ? List.of() : instrumentMappings);
    dividendEntitlementOverrides = List.copyOf(dividendEntitlementOverrides == null ? List.of()
        : dividendEntitlementOverrides);
    optionExpiryAttestations = List.copyOf(optionExpiryAttestations == null ? List.of() : optionExpiryAttestations);
  }
}
