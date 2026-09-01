package com.personal.investment.ledger.interfaces;

import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.application.LedgerExportFile;
import com.personal.investment.ledger.application.LedgerExportFormat;
import com.personal.investment.ledger.application.LedgerExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated, owner-only download surface. It is intentionally separate from untrusted file-upload routes. */
@RestController
@RequestMapping("/api/v1/ledger/exports")
public class LedgerExportController {
  private final LedgerExportService exportService;

  public LedgerExportController(LedgerExportService exportService) {
    this.exportService = exportService;
  }

  @GetMapping
  public ResponseEntity<byte[]> export(@RequestParam LedgerExportFormat format, Authentication authentication) {
    LedgerExportFile file = exportService.generate(ownerUserId(authentication), format);
    String filename = "investment-ledger-" + file.exportId() + "." + file.format().extension();
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.format().mediaType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
        .header("X-Export-Id", file.exportId())
        .header("X-Content-SHA256", file.contentSha256Hex())
        .body(file.content());
  }

  private static String ownerUserId(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof SessionAuthenticationPrincipal principal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return principal.user().userId();
  }
}
