package com.personal.investment.ledger.interfaces;

import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.application.LedgerSnapshot;
import com.personal.investment.ledger.application.LedgerSnapshotFile;
import com.personal.investment.ledger.application.LedgerSnapshotRestoreResult;
import com.personal.investment.ledger.application.LedgerSnapshotRestoreService;
import com.personal.investment.ledger.application.LedgerSnapshotService;
import com.personal.investment.platform.application.IdempotencyExecutor;
import com.personal.investment.platform.application.IdempotencyResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Owner-only snapshot metadata and recovery API. Recovery is intentionally blocked unless the ledger is empty. */
@RestController
@RequestMapping("/api/v1/ledger/snapshots")
public class LedgerSnapshotController {
  private final LedgerSnapshotService snapshotService;
  private final LedgerSnapshotRestoreService restoreService;
  private final IdempotencyExecutor idempotencyExecutor;

  public LedgerSnapshotController(LedgerSnapshotService snapshotService, LedgerSnapshotRestoreService restoreService,
      IdempotencyExecutor idempotencyExecutor) {
    this.snapshotService = snapshotService;
    this.restoreService = restoreService;
    this.idempotencyExecutor = idempotencyExecutor;
  }

  @GetMapping
  public SnapshotListResponse list(@RequestParam(defaultValue = "20") int limit, Authentication authentication) {
    String ownerUserId = owner(authentication);
    return new SnapshotListResponse(snapshotService.list(ownerUserId, limit).stream().map(LedgerSnapshotController::response).toList());
  }

  @PostMapping
  public ResponseEntity<SnapshotResponse> create(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      Authentication authentication) {
    String ownerUserId = owner(authentication);
    IdempotencyResponse<SnapshotResponse> response = idempotencyExecutor.execute(ownerUserId, "POST",
        "/api/v1/ledger/snapshots", key, "manual-ledger-snapshot", SnapshotResponse.class,
        () -> new IdempotencyResponse<>(201, response(snapshotService.create(ownerUserId))));
    return ResponseEntity.status(response.status()).body(response.body());
  }

  @GetMapping("/{ledgerSnapshotId}/download")
  public ResponseEntity<byte[]> download(
      @PathVariable @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String ledgerSnapshotId,
      Authentication authentication) {
    LedgerSnapshotFile file = snapshotService.download(owner(authentication), ledgerSnapshotId);
    String filename = "investment-ledger-snapshot-" + file.snapshot().ledgerSnapshotId() + ".json";
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
        .header("X-Ledger-Snapshot-Id", file.snapshot().ledgerSnapshotId())
        .header("X-Content-SHA256", file.snapshot().contentSha256Hex()).body(file.content());
  }

  @PostMapping("/{ledgerSnapshotId}/restore")
  public ResponseEntity<RestoreResponse> restore(
      @PathVariable @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String ledgerSnapshotId,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key, Authentication authentication) {
    String ownerUserId = owner(authentication);
    String path = "/api/v1/ledger/snapshots/" + ledgerSnapshotId + "/restore";
    IdempotencyResponse<RestoreResponse> response = idempotencyExecutor.execute(ownerUserId, "POST", path, key,
        ledgerSnapshotId.length() + ":" + ledgerSnapshotId, RestoreResponse.class,
        () -> new IdempotencyResponse<>(201, restoreResponse(restoreService.restoreIntoEmptyWorkspace(ownerUserId, ledgerSnapshotId))));
    return ResponseEntity.status(response.status()).body(response.body());
  }

  private static String owner(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof SessionAuthenticationPrincipal principal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return principal.user().userId();
  }

  private static SnapshotResponse response(LedgerSnapshot snapshot) {
    return new SnapshotResponse(snapshot.ledgerSnapshotId(), snapshot.asOfDate(),
        Long.toString(snapshot.sourceLedgerVersion()), snapshot.contentSha256Hex(), snapshot.createdAt());
  }

  private static RestoreResponse restoreResponse(LedgerSnapshotRestoreResult result) {
    return new RestoreResponse(result.ledgerSnapshotId(), result.restoredAccountCount(), result.restoredTransactionCount(),
        Long.toString(result.targetLedgerVersion()));
  }

  public record SnapshotListResponse(List<SnapshotResponse> items) { }

  public record SnapshotResponse(String ledgerSnapshotId, LocalDate asOfDate, String sourceLedgerVersion,
                                 String contentSha256Hex, Instant createdAt) { }

  public record RestoreResponse(String ledgerSnapshotId, int restoredAccountCount, int restoredTransactionCount,
                                String targetLedgerVersion) { }
}
