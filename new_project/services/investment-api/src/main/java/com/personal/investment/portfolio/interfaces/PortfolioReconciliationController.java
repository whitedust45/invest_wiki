package com.personal.investment.portfolio.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.interfaces.QuantityWireParser;
import com.personal.investment.ledger.interfaces.StrictStringDeserializer;
import com.personal.investment.platform.application.IdempotencyExecutor;
import com.personal.investment.platform.application.IdempotencyResponse;
import com.personal.investment.portfolio.application.BrokerPosition;
import com.personal.investment.portfolio.application.PortfolioReconciliationCommand;
import com.personal.investment.portfolio.application.PortfolioReconciliationPage;
import com.personal.investment.portfolio.application.PortfolioReconciliationQueryService;
import com.personal.investment.portfolio.application.PortfolioReconciliationService;
import com.personal.investment.portfolio.application.PortfolioReconciliationView;
import com.personal.investment.portfolio.domain.PortfolioReconciliation;
import com.personal.investment.portfolio.domain.ReconciliationPosition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/portfolio/reconciliations")
public class PortfolioReconciliationController {
  private static final String PATH = "/api/v1/portfolio/reconciliations";

  private final PortfolioReconciliationService reconciliationService;
  private final PortfolioReconciliationQueryService reconciliationQueryService;
  private final IdempotencyExecutor idempotencyExecutor;

  public PortfolioReconciliationController(PortfolioReconciliationService reconciliationService,
      PortfolioReconciliationQueryService reconciliationQueryService, IdempotencyExecutor idempotencyExecutor) {
    this.reconciliationService = reconciliationService;
    this.reconciliationQueryService = reconciliationQueryService;
    this.idempotencyExecutor = idempotencyExecutor;
  }

  @GetMapping
  public ReconciliationPageResponse list(@RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "30") int limit, @RequestParam(required = false) String cashAccountId,
      @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
      Authentication authentication) {
    PortfolioReconciliationPage page = reconciliationQueryService.list(ownerUserId(authentication), cursor, limit,
        emptyToNull(cashAccountId), from, to);
    return new ReconciliationPageResponse(page.items().stream().map(PortfolioReconciliationController::toResponse)
        .toList(), page.nextCursor());
  }

  @PostMapping
  public ResponseEntity<ReconciliationResponse> create(
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @Valid @RequestBody ReconciliationCreateRequest request, Authentication authentication) {
    String ownerUserId = ownerUserId(authentication);
    IdempotencyResponse<ReconciliationResponse> response = idempotencyExecutor.execute(ownerUserId, "POST", PATH,
        key, canonical(request), ReconciliationResponse.class, () -> new IdempotencyResponse<>(HttpStatus.CREATED.value(),
            toResponse(reconciliationService.record(ownerUserId, command(request)))));
    return ResponseEntity.status(response.status()).body(response.body());
  }

  private static PortfolioReconciliationCommand command(ReconciliationCreateRequest request) {
    return new PortfolioReconciliationCommand(request.cashAccountId(), request.reconciliationDate(),
        nonNegativeMinorUnit(request.brokerCashCent(), "broker_cash_cent"), request.positions().stream()
            .map(position -> new BrokerPosition(position.instrumentId(),
                QuantityWireParser.parsePositive(position.quantity(), "positions.quantity"))).toList(),
        emptyToNull(request.attachmentImportExportFileId()), emptyToNull(request.discrepancyReason()));
  }

  private static long nonNegativeMinorUnit(String value, String field) {
    if (value == null || !value.matches("(?:0|[1-9][0-9]*)")) {
      throw new IllegalArgumentException(field + " must be a nonnegative integer string");
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(field + " exceeds long range", exception);
    }
  }

  private static ReconciliationResponse toResponse(PortfolioReconciliation reconciliation) {
    return new ReconciliationResponse(reconciliation.reconciliationId(), reconciliation.cashAccountId(),
        reconciliation.reconciliationDate(), Long.toString(reconciliation.brokerCashCent()),
        Long.toString(reconciliation.ledgerCashCent()), Long.toString(reconciliation.cashDifferenceCent()),
        reconciliation.cashDifferenceDirection().name(), reconciliation.currency().name(), reconciliation.status().name(),
        reconciliation.sourceLedgerVersion(), reconciliation.positions().stream().map(PortfolioReconciliationController::toPosition)
            .toList());
  }

  private static ReconciliationResponse toResponse(PortfolioReconciliationView reconciliation) {
    return new ReconciliationResponse(reconciliation.reconciliationId(), reconciliation.cashAccountId(),
        reconciliation.reconciliationDate(), reconciliation.brokerCashCent(), reconciliation.ledgerCashCent(),
        reconciliation.cashDifferenceCent(), reconciliation.cashDifferenceDirection().name(), reconciliation.currency().name(),
        reconciliation.status().name(), reconciliation.sourceLedgerVersion(), reconciliation.positions().stream()
            .map(PortfolioReconciliationController::toPosition).toList());
  }

  private static PositionResponse toPosition(ReconciliationPosition position) {
    return new PositionResponse(position.instrumentId(), position.brokerQuantity().toPlainString(),
        position.ledgerQuantity().toPlainString(), position.quantityDifference().toPlainString());
  }

  private static String canonical(ReconciliationCreateRequest request) {
    String positions = request.positions().stream().sorted(Comparator.comparing(PositionRequest::instrumentId))
        .map(position -> part(position.instrumentId()) + part(position.quantity())).reduce("", String::concat);
    return part(request.cashAccountId()) + part(request.reconciliationDate().toString()) + part(request.brokerCashCent())
        + part(positions) + part(request.attachmentImportExportFileId()) + part(request.discrepancyReason());
  }

  private static String part(String value) {
    return value == null ? "-1:" : value.length() + ":" + value;
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String ownerUserId(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (!(principal instanceof SessionAuthenticationPrincipal sessionPrincipal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return sessionPrincipal.user().userId();
  }

  public record ReconciliationCreateRequest(
      @NotBlank String cashAccountId,
      @NotNull LocalDate reconciliationDate,
      @JsonDeserialize(using = StrictStringDeserializer.class) String brokerCashCent,
      @NotNull List<@Valid PositionRequest> positions,
      String attachmentImportExportFileId,
      @Size(max = 1000) String discrepancyReason) {
  }

  public record PositionRequest(@NotBlank String instrumentId,
                                @JsonDeserialize(using = StrictStringDeserializer.class) String quantity) {
  }

  public record ReconciliationResponse(String reconciliationId, String cashAccountId, LocalDate reconciliationDate,
                                       String brokerCashCent, String ledgerCashCent, String cashDifferenceCent,
                                       String cashDifferenceDirection, String currency, String status,
                                       long sourceLedgerVersion, List<PositionResponse> positions) {
  }

  public record ReconciliationPageResponse(List<ReconciliationResponse> items, String nextCursor) {
  }

  public record PositionResponse(String instrumentId, String brokerQuantity, String ledgerQuantity,
                                 String quantityDifference) {
  }
}
