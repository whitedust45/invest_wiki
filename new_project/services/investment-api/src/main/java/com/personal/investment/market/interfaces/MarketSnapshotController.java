package com.personal.investment.market.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.interfaces.PositiveMinorUnitParser;
import com.personal.investment.ledger.interfaces.StrictStringDeserializer;
import com.personal.investment.market.application.MarketSnapshotService;
import com.personal.investment.market.application.MarketSnapshotSubmissionCommand;
import com.personal.investment.market.application.MarketSnapshotSubmissionResult;
import com.personal.investment.market.application.MarketSyncRun;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/market")
public class MarketSnapshotController {
  private final MarketSnapshotService service;

  public MarketSnapshotController(MarketSnapshotService service) {
    this.service = service;
  }

  @PostMapping("/snapshot-submissions")
  public ResponseEntity<MarketSnapshotSubmissionResult> submit(@Valid @RequestBody SnapshotSubmissionRequest request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.submit(ownerUserId(authentication), command(request)));
  }

  @GetMapping("/sync-runs/{marketSyncRunId}")
  public MarketSyncRunResponse findRun(@PathVariable String marketSyncRunId) {
    MarketSyncRun run = service.findRun(marketSyncRunId)
        .orElseThrow(() -> new IllegalArgumentException("market sync run was not found"));
    return new MarketSyncRunResponse(run.marketSyncRunId(), run.tradingDate(), run.runType(), run.status(),
        run.startedAt(), run.completedAt());
  }

  private static MarketSnapshotSubmissionCommand command(SnapshotSubmissionRequest request) {
    return new MarketSnapshotSubmissionCommand(request.tradingDate(), request.sourceName(), request.sourceReference(),
        request.quotes().stream().map(value -> new MarketSnapshotSubmissionCommand.Quote(value.instrumentId(),
            value.quoteTime(), value.sourceObservationKey(), PositiveMinorUnitParser.parse(value.priceCent(), "priceCent"),
            optionalPositiveNullable(value.prevCloseCent(), "prevCloseCent"), value.currency())).toList(),
        request.metrics().stream().map(value -> new MarketSnapshotSubmissionCommand.Metric(value.instrumentId(),
            value.metricName(), decimal(value.metricValueDecimal(), "metricValueDecimal"), value.sourceObservationKey()))
            .toList(),
        request.basis().stream().map(value -> new MarketSnapshotSubmissionCommand.Basis(value.underlyingInstrumentId(),
            value.futureInstrumentId(), decimal(value.spotPricePoints(), "spotPricePoints"),
            decimal(value.futurePricePoints(), "futurePricePoints"), optionalDecimal(value.annualizedBasisDecimal(),
                "annualizedBasisDecimal"), value.maturityDate(), value.daysLeft(), value.sourceObservationKey())).toList());
  }

  private static Long optionalPositiveNullable(String value, String field) {
    return value == null ? null : PositiveMinorUnitParser.parse(value, field);
  }

  private static BigDecimal decimal(String value, String field) {
    try {
      return new BigDecimal(value);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(field + " must be a decimal string", exception);
    }
  }

  private static BigDecimal optionalDecimal(String value, String field) {
    return value == null ? null : decimal(value, field);
  }

  private static String ownerUserId(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (!(principal instanceof SessionAuthenticationPrincipal sessionPrincipal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return sessionPrincipal.user().userId();
  }

  public record SnapshotSubmissionRequest(@NotNull LocalDate tradingDate, @NotBlank @Size(max = 64) String sourceName,
      @NotBlank @Size(max = 512) String sourceReference, @Valid List<QuoteRequest> quotes,
      @Valid List<MetricRequest> metrics, @Valid List<BasisRequest> basis) {
    public SnapshotSubmissionRequest {
      quotes = quotes == null ? List.of() : List.copyOf(quotes);
      metrics = metrics == null ? List.of() : List.copyOf(metrics);
      basis = basis == null ? List.of() : List.copyOf(basis);
    }
  }

  public record QuoteRequest(@NotBlank String instrumentId, @NotNull Instant quoteTime,
      @NotBlank @Size(max = 256) String sourceObservationKey,
      @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String priceCent,
      @JsonDeserialize(using = StrictStringDeserializer.class) String prevCloseCent, @NotNull CurrencyCode currency) {
  }

  public record MetricRequest(@NotBlank String instrumentId, @NotBlank @Size(max = 64) String metricName,
      @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String metricValueDecimal,
      @NotBlank @Size(max = 256) String sourceObservationKey) {
  }

  public record BasisRequest(@NotBlank String underlyingInstrumentId, @NotBlank String futureInstrumentId,
      @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String spotPricePoints,
      @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String futurePricePoints,
      @JsonDeserialize(using = StrictStringDeserializer.class) String annualizedBasisDecimal, LocalDate maturityDate,
      Integer daysLeft, @NotBlank @Size(max = 256) String sourceObservationKey) {
  }

  public record MarketSyncRunResponse(String marketSyncRunId, LocalDate tradingDate, String runType, String status,
                                       Instant startedAt, Instant completedAt) {
  }
}
