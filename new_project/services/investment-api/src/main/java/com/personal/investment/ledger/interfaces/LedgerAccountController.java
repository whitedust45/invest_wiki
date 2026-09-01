package com.personal.investment.ledger.interfaces;

import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.application.LedgerAccountService;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import com.personal.investment.platform.application.IdempotencyExecutor;
import com.personal.investment.platform.application.IdempotencyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/ledger/accounts")
public class LedgerAccountController {
  private static final String PATH = "/api/v1/ledger/accounts";

  private final LedgerAccountService accountService;
  private final IdempotencyExecutor idempotencyExecutor;

  public LedgerAccountController(LedgerAccountService accountService,
      IdempotencyExecutor idempotencyExecutor) {
    this.accountService = accountService;
    this.idempotencyExecutor = idempotencyExecutor;
  }

  @PostMapping
  public ResponseEntity<CashAccountResponse> create(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @Valid @RequestBody CreateCashAccountRequest request, Authentication authentication) {
    String ownerUserId = ownerUserId(authentication);
    IdempotencyResponse<CashAccountResponse> idempotencyResponse = idempotencyExecutor.execute(
        ownerUserId, "POST", PATH, key, canonical(request),
        CashAccountResponse.class, () -> new IdempotencyResponse<>(HttpStatus.CREATED.value(),
            response(accountService.createCashAccount(ownerUserId, request.displayName(),
                CurrencyCode.of(request.currency())))));
    return ResponseEntity.status(idempotencyResponse.status()).body(idempotencyResponse.body());
  }

  @GetMapping
  public CashAccountListResponse list(Authentication authentication) {
    return new CashAccountListResponse(accountService.listCashAccounts(ownerUserId(authentication)).stream()
        .map(this::response).toList());
  }

  @PostMapping("/{accountId}/disable")
  public ResponseEntity<CashAccountResponse> disable(
      @PathVariable @NotBlank String accountId,
      @RequestHeader("If-Match") @NotBlank @Size(max = 32) String ifMatch,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      Authentication authentication) {
    String ownerUserId = ownerUserId(authentication);
    long expectedVersion = parseIfMatch(ifMatch);
    String path = PATH + "/" + accountId + "/disable";
    IdempotencyResponse<CashAccountResponse> idempotencyResponse = idempotencyExecutor.execute(
        ownerUserId, "POST", path, key, accountId.length() + ":" + accountId + ":" + expectedVersion,
        CashAccountResponse.class, () -> new IdempotencyResponse<>(HttpStatus.OK.value(),
            response(accountService.disableCashAccount(ownerUserId, accountId, expectedVersion))));
    return ResponseEntity.status(idempotencyResponse.status()).body(idempotencyResponse.body());
  }

  private CashAccountResponse response(LedgerAccount account) {
    return new CashAccountResponse(account.accountId(), account.displayName(), account.accountKind(),
        account.currency(), account.status(), Long.toString(account.version()));
  }

  private static String ownerUserId(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (!(principal instanceof SessionAuthenticationPrincipal sessionPrincipal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return sessionPrincipal.user().userId();
  }

  private static String canonical(CreateCashAccountRequest request) {
    return request.displayName().length() + ":" + request.displayName() + ":" + request.currency();
  }

  private static long parseIfMatch(String ifMatch) {
    String value = ifMatch.length() >= 2 && ifMatch.startsWith("\"") && ifMatch.endsWith("\"")
        ? ifMatch.substring(1, ifMatch.length() - 1) : ifMatch;
    if (!value.matches("[0-9]+")) {
      throw new IllegalArgumentException("If-Match must be a non-negative account version");
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("If-Match account version exceeds long range", exception);
    }
  }

  public record CreateCashAccountRequest(
      @NotBlank @Size(max = 128) String displayName,
      @NotBlank @Pattern(regexp = "CNY|USD") String currency) {
  }

  public record CashAccountResponse(String accountId, String displayName, LedgerAccountKind accountKind,
                                    CurrencyCode currency, LedgerAccountStatus status, String version) {
  }

  public record CashAccountListResponse(List<CashAccountResponse> items) {
  }
}
