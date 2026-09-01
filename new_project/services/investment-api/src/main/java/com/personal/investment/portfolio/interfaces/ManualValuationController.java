package com.personal.investment.portfolio.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.interfaces.PositiveMinorUnitParser;
import com.personal.investment.ledger.interfaces.StrictStringDeserializer;
import com.personal.investment.platform.application.IdempotencyExecutor;
import com.personal.investment.platform.application.IdempotencyResponse;
import com.personal.investment.portfolio.application.ManualValuationCommand;
import com.personal.investment.portfolio.application.ManualValuationService;
import com.personal.investment.portfolio.domain.ManualValuation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/portfolio/manual-valuations")
public class ManualValuationController {
  private static final String PATH = "/api/v1/portfolio/manual-valuations";

  private final ManualValuationService manualValuationService;
  private final IdempotencyExecutor idempotencyExecutor;

  public ManualValuationController(ManualValuationService manualValuationService,
      IdempotencyExecutor idempotencyExecutor) {
    this.manualValuationService = manualValuationService;
    this.idempotencyExecutor = idempotencyExecutor;
  }

  @PostMapping
  public ResponseEntity<ManualValuationResponse> create(
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @Valid @RequestBody ManualValuationRequest request, Authentication authentication) {
    String ownerUserId = ownerUserId(authentication);
    IdempotencyResponse<ManualValuationResponse> response = idempotencyExecutor.execute(ownerUserId, "POST", PATH,
        key, canonical(request), ManualValuationResponse.class, () -> new IdempotencyResponse<>(HttpStatus.CREATED.value(),
            toResponse(manualValuationService.record(ownerUserId, command(request)))));
    return ResponseEntity.status(response.status()).body(response.body());
  }

  private static ManualValuationCommand command(ManualValuationRequest request) {
    Long unitPriceCent = optionalPositive(request.unitPriceCent(), "unit_price_cent");
    Long marketValueCent = optionalPositive(request.marketValueCent(), "market_value_cent");
    return new ManualValuationCommand(request.instrumentId(), request.valuationDate(), request.currency(), unitPriceCent,
        marketValueCent, request.validUntil(), request.note());
  }

  private static Long optionalPositive(String value, String field) {
    return value == null ? null : PositiveMinorUnitParser.parse(value, field);
  }

  private static ManualValuationResponse toResponse(ManualValuation valuation) {
    ValuationStatus status = valuation.validUntil() != null && !valuation.validUntil().isAfter(Instant.now())
        ? ValuationStatus.EXPIRED : ValuationStatus.ACTIVE;
    return new ManualValuationResponse(valuation.manualValuationId(), valuation.instrumentId(),
        valuation.valuationDate(), valuation.unitPriceCent() == null ? null : Long.toString(valuation.unitPriceCent()),
        valuation.marketValueCent() == null ? null : Long.toString(valuation.marketValueCent()),
        valuation.currency(), valuation.priority(), valuation.validUntil(), status);
  }

  private static String canonical(ManualValuationRequest request) {
    return part(request.instrumentId()) + part(request.valuationDate().toString()) + part(request.currency().name())
        + part(request.unitPriceCent()) + part(request.marketValueCent())
        + part(request.validUntil() == null ? null : request.validUntil().toString()) + part(request.note());
  }

  private static String part(String value) {
    return value == null ? "-1:" : value.length() + ":" + value;
  }

  private static String ownerUserId(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (!(principal instanceof SessionAuthenticationPrincipal sessionPrincipal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return sessionPrincipal.user().userId();
  }

  public record ManualValuationRequest(
      @NotBlank String instrumentId,
      @NotNull LocalDate valuationDate,
      @NotNull CurrencyCode currency,
      @JsonDeserialize(using = StrictStringDeserializer.class) String unitPriceCent,
      @JsonDeserialize(using = StrictStringDeserializer.class) String marketValueCent,
      Instant validUntil,
      @Size(max = 1000) String note) {
  }

  public record ManualValuationResponse(String manualValuationId, String instrumentId, LocalDate valuationDate,
                                        String unitPriceCent, String marketValueCent, CurrencyCode currency,
                                        short priority, Instant validUntil, ValuationStatus valuationStatus) {
  }

  public enum ValuationStatus {
    ACTIVE,
    EXPIRED
  }
}
