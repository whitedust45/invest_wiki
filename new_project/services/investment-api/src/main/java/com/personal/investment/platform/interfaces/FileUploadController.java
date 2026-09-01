package com.personal.investment.platform.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.interfaces.PositiveMinorUnitParser;
import com.personal.investment.ledger.interfaces.StrictStringDeserializer;
import com.personal.investment.platform.application.FileUploadRequestService;
import com.personal.investment.platform.application.FileScanRequestResult;
import com.personal.investment.platform.application.FileScanRequestService;
import com.personal.investment.platform.application.ImportExportFileQueryService;
import com.personal.investment.platform.application.ImportExportFileView;
import com.personal.investment.platform.application.IdempotencyExecutor;
import com.personal.investment.platform.application.IdempotencyResponse;
import com.personal.investment.platform.application.ImportExportFileDirection;
import com.personal.investment.platform.application.ImportExportFileStatus;
import com.personal.investment.platform.application.UploadRequestCommand;
import com.personal.investment.platform.application.UploadRequestResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/files")
public class FileUploadController {
  private static final String PATH = "/api/v1/files/upload-requests";

  private final FileUploadRequestService uploadRequestService;
  private final FileScanRequestService scanRequestService;
  private final ImportExportFileQueryService fileQueryService;
  private final IdempotencyExecutor idempotencyExecutor;

  public FileUploadController(FileUploadRequestService uploadRequestService, FileScanRequestService scanRequestService,
      ImportExportFileQueryService fileQueryService, IdempotencyExecutor idempotencyExecutor) {
    this.uploadRequestService = uploadRequestService;
    this.scanRequestService = scanRequestService;
    this.fileQueryService = fileQueryService;
    this.idempotencyExecutor = idempotencyExecutor;
  }

  @PostMapping("/upload-requests")
  public ResponseEntity<UploadRequestResponse> requestUpload(
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @Valid @RequestBody UploadRequest request, Authentication authentication) {
    String ownerUserId = ownerUserId(authentication);
    IdempotencyResponse<UploadRequestResponse> response = idempotencyExecutor.execute(ownerUserId, "POST", PATH,
        key, canonical(request), UploadRequestResponse.class,
        () -> new IdempotencyResponse<>(HttpStatus.CREATED.value(), toResponse(uploadRequestService.request(ownerUserId,
            new UploadRequestCommand(request.direction(), request.mediaType(),
                PositiveMinorUnitParser.parse(request.byteSize(), "byte_size"), request.sha256())))));
    return ResponseEntity.status(response.status()).body(response.body());
  }

  @PostMapping("/{importExportFileId}/scan-requests")
  public ResponseEntity<FileScanRequestResponse> requestScan(
      @PathVariable @NotBlank @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String importExportFileId,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      Authentication authentication) {
    String ownerUserId = ownerUserId(authentication);
    String path = "/api/v1/files/" + importExportFileId + "/scan-requests";
    IdempotencyResponse<FileScanRequestResponse> response = idempotencyExecutor.execute(ownerUserId, "POST", path,
        key, part(importExportFileId), FileScanRequestResponse.class,
        () -> new IdempotencyResponse<>(HttpStatus.ACCEPTED.value(),
            toResponse(scanRequestService.request(ownerUserId, importExportFileId))));
    return ResponseEntity.status(response.status()).body(response.body());
  }

  @GetMapping("/{importExportFileId}")
  public FileStatusResponse getFileStatus(
      @PathVariable @NotBlank @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String importExportFileId,
                                          Authentication authentication) {
    return toResponse(fileQueryService.get(ownerUserId(authentication), importExportFileId));
  }

  private static UploadRequestResponse toResponse(UploadRequestResult result) {
    return new UploadRequestResponse(result.importExportFileId(), result.uploadUrl(), result.method(), result.fileField(),
        result.formData(), result.expiresAt());
  }

  private static FileScanRequestResponse toResponse(FileScanRequestResult result) {
    return new FileScanRequestResponse(result.importExportFileId(), result.status());
  }

  private static FileStatusResponse toResponse(ImportExportFileView result) {
    return new FileStatusResponse(result.importExportFileId(), result.direction(), result.mediaType(),
        Long.toString(result.byteSize()), result.status());
  }

  private static String canonical(UploadRequest request) {
    return part(request.direction().name()) + part(request.mediaType()) + part(request.byteSize())
        + part(request.sha256());
  }

  private static String part(String value) {
    return value.length() + ":" + value;
  }

  private static String ownerUserId(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof SessionAuthenticationPrincipal principal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return principal.user().userId();
  }

  public record UploadRequest(
      @NotNull ImportExportFileDirection direction,
      @NotBlank @Size(max = 128) String mediaType,
      @NotBlank @JsonDeserialize(using = StrictStringDeserializer.class) String byteSize,
      @NotBlank @JsonDeserialize(using = StrictStringDeserializer.class) String sha256) {
  }

  public record UploadRequestResponse(String importExportFileId, String uploadUrl, String method, String fileField,
                                      Map<String, String> formData, Instant expiresAt) {
  }

  public record FileScanRequestResponse(String importExportFileId, ImportExportFileStatus status) {
  }

  public record FileStatusResponse(String importExportFileId, ImportExportFileDirection direction, String mediaType,
                                   String byteSize, ImportExportFileStatus status) {
  }
}
