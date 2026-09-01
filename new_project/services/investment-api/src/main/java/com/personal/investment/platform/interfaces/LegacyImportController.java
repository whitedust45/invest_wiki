package com.personal.investment.platform.interfaces;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.interfaces.StrictStringDeserializer;
import com.personal.investment.platform.application.CreateLegacyImportPreviewCommand;
import com.personal.investment.platform.application.IdempotencyExecutor;
import com.personal.investment.platform.application.IdempotencyResponse;
import com.personal.investment.platform.application.LegacyAmountUnit;
import com.personal.investment.platform.application.LegacyCurrencyMapping;
import com.personal.investment.platform.application.LegacyDividendEntitlementOverride;
import com.personal.investment.platform.application.LegacyImportFormat;
import com.personal.investment.platform.application.LegacyImportPreview;
import com.personal.investment.platform.application.LegacyImportPreviewLine;
import com.personal.investment.platform.application.LegacyImportPreviewPayload;
import com.personal.investment.platform.application.LegacyImportPreviewStatus;
import com.personal.investment.platform.application.LegacyImportService;
import com.personal.investment.platform.application.LegacyInstrumentMapping;
import com.personal.investment.platform.application.LegacyOptionExpiryAttestation;
import com.personal.investment.ledger.application.OptionExpiryOutcome;
import com.personal.investment.ledger.domain.CurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class LegacyImportController {
  private final LegacyImportService importService;
  private final IdempotencyExecutor idempotencyExecutor;
  private final ObjectMapper objectMapper;

  public LegacyImportController(LegacyImportService importService, IdempotencyExecutor idempotencyExecutor,
      ObjectMapper objectMapper) {
    this.importService = importService;
    this.idempotencyExecutor = idempotencyExecutor;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/ledger/imports")
  public ResponseEntity<ImportJobResponse> create(
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @Valid @RequestBody ImportRequest request, Authentication authentication) {
    String owner = owner(authentication);
    IdempotencyResponse<ImportJobResponse> response = idempotencyExecutor.execute(owner, "POST", "/api/v1/ledger/imports",
        key, canonical(request), ImportJobResponse.class, () -> new IdempotencyResponse<>(HttpStatus.ACCEPTED.value(),
            response(importService.createPreview(owner, command(request)))));
    return ResponseEntity.status(response.status()).body(response.body());
  }

  @GetMapping("/jobs/{jobId}")
  public ImportJobResponse get(@PathVariable @NotBlank @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String jobId,
      Authentication authentication) {
    return response(importService.findJob(owner(authentication), jobId));
  }

  @PostMapping("/ledger/imports/{jobId}/confirm")
  public ResponseEntity<ImportJobResponse> confirm(
      @PathVariable @NotBlank @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String jobId,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @Valid @RequestBody ConfirmRequest request, Authentication authentication) {
    String owner = owner(authentication);
    String path = "/api/v1/ledger/imports/" + jobId + "/confirm";
    IdempotencyResponse<ImportJobResponse> response = idempotencyExecutor.execute(owner, "POST", path, key,
        jobId.length() + ":" + jobId + request.expectedChecksum().length() + ":" + request.expectedChecksum(),
        ImportJobResponse.class, () -> new IdempotencyResponse<>(HttpStatus.OK.value(),
            response(importService.confirm(owner, jobId, request.expectedChecksum()))));
    return ResponseEntity.status(response.status()).body(response.body());
  }

  private CreateLegacyImportPreviewCommand command(ImportRequest request) {
    return new CreateLegacyImportPreviewCommand(request.importExportFileId(), request.format(), request.snapshotId(),
        request.currencyMappings().stream().map(mapping -> new LegacyCurrencyMapping(mapping.module(), mapping.action(),
            mapping.currency(), mapping.amountUnit(), mapping.cashAccountId())).toList(),
        request.instrumentMappings().stream().map(mapping -> new LegacyInstrumentMapping(mapping.module(), mapping.symbol(),
            mapping.instrumentId())).toList(), request.dividendEntitlementOverrides().stream()
            .map(override -> new LegacyDividendEntitlementOverride(override.sourceRow(), override.entitlementDate())).toList(),
        request.optionExpiryAttestations().stream().map(attestation -> new LegacyOptionExpiryAttestation(
            attestation.sourceRow(), attestation.expiryOutcome())).toList());
  }

  private ImportJobResponse response(LegacyImportPreview preview) {
    try {
      LegacyImportPreviewPayload payload = objectMapper.readValue(preview.previewJson(), LegacyImportPreviewPayload.class);
      return new ImportJobResponse(preview.jobId(), preview.importPreviewId(), preview.importExportFileId(), preview.format(),
          preview.sourceSnapshotId(), preview.previewChecksumHex(), preview.status(), preview.expiresAt(),
          payload.applicableCount(), payload.needsReviewCount(), payload.lines());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("persisted import preview JSON is invalid", exception);
    }
  }

  private static String canonical(ImportRequest request) {
    return request.toString();
  }

  private static String owner(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof SessionAuthenticationPrincipal principal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return principal.user().userId();
  }

  public record ImportRequest(
      @NotBlank @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String importExportFileId,
      @NotNull LegacyImportFormat format,
      @Size(max = 64) String snapshotId,
      List<@Valid CurrencyMappingRequest> currencyMappings,
      List<@Valid InstrumentMappingRequest> instrumentMappings,
      List<@Valid DividendEntitlementOverrideRequest> dividendEntitlementOverrides,
      List<@Valid OptionExpiryAttestationRequest> optionExpiryAttestations) {
    public ImportRequest {
      currencyMappings = List.copyOf(currencyMappings == null ? List.of() : currencyMappings);
      instrumentMappings = List.copyOf(instrumentMappings == null ? List.of() : instrumentMappings);
      dividendEntitlementOverrides = List.copyOf(dividendEntitlementOverrides == null ? List.of()
          : dividendEntitlementOverrides);
      optionExpiryAttestations = List.copyOf(optionExpiryAttestations == null ? List.of()
          : optionExpiryAttestations);
    }
  }

  public record CurrencyMappingRequest(@NotBlank String module, String action, @NotNull CurrencyCode currency,
      @NotNull LegacyAmountUnit amountUnit,
      @NotBlank @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String cashAccountId) {
  }

  public record InstrumentMappingRequest(@NotBlank String module, @NotBlank String symbol,
      @NotBlank @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String instrumentId) {
  }

  public record DividendEntitlementOverrideRequest(int sourceRow, @NotNull LocalDate entitlementDate) {
  }

  public record OptionExpiryAttestationRequest(int sourceRow, @NotNull OptionExpiryOutcome expiryOutcome) {
  }

  public record ConfirmRequest(
      @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") @JsonDeserialize(using = StrictStringDeserializer.class)
      String expectedChecksum) {
  }

  public record ImportJobResponse(String jobId, String importPreviewId, String importExportFileId,
      LegacyImportFormat format, String sourceSnapshotId, String previewChecksum, LegacyImportPreviewStatus status,
      Instant expiresAt, int applicableCount, int needsReviewCount, List<LegacyImportPreviewLine> lines) {
  }
}
