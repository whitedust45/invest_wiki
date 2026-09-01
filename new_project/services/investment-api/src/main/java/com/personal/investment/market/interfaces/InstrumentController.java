package com.personal.investment.market.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.interfaces.PositiveMinorUnitParser;
import com.personal.investment.ledger.interfaces.StrictStringDeserializer;
import com.personal.investment.market.application.CreateInstrumentCommand;
import com.personal.investment.market.application.InstrumentService;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.FutureSpecification;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.market.domain.OptionRight;
import com.personal.investment.market.domain.OptionSpecification;
import com.personal.investment.platform.application.IdempotencyExecutor;
import com.personal.investment.platform.application.IdempotencyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/market/instruments")
public class InstrumentController {
  private static final String PATH = "/api/v1/market/instruments";

  private final InstrumentService instrumentService;
  private final IdempotencyExecutor idempotencyExecutor;

  public InstrumentController(InstrumentService instrumentService, IdempotencyExecutor idempotencyExecutor) {
    this.instrumentService = instrumentService;
    this.idempotencyExecutor = idempotencyExecutor;
  }

  @PostMapping
  public ResponseEntity<InstrumentResponse> create(
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @Valid @RequestBody CreateInstrumentRequest request, Authentication authentication) {
    String ownerUserId = ownerUserId(authentication);
    IdempotencyResponse<InstrumentResponse> result = idempotencyExecutor.execute(ownerUserId, "POST", PATH, key,
        canonical(request), InstrumentResponse.class, () -> new IdempotencyResponse<>(HttpStatus.CREATED.value(),
            response(instrumentService.create(command(request)))));
    return ResponseEntity.status(result.status()).body(result.body());
  }

  private static CreateInstrumentCommand command(CreateInstrumentRequest request) {
    FutureSpecification future = request.future() == null ? null : new FutureSpecification(
        request.future().productCode(), PositiveMinorUnitParser.parse(request.future().contractMultiplierCent(),
            "future.contract_multiplier_cent"));
    OptionSpecification option = request.option() == null ? null : new OptionSpecification(
        request.option().underlyingInstrumentId(), request.option().optionRight(),
        PositiveMinorUnitParser.parse(request.option().strikePriceCent(), "option.strike_price_cent"),
        PositiveMinorUnitParser.parse(request.option().contractMultiplier(), "option.contract_multiplier"));
    return new CreateInstrumentCommand(request.market(), request.exchange(), request.symbol(), request.displayName(),
        request.assetType(), request.nativeCurrency(), request.maturityDate(), future, option, request.tushareCode(),
        request.underlyingInstrumentId());
  }

  private static InstrumentResponse response(Instrument instrument) {
    FutureResponse future = instrument.futureSpecification() == null ? null : new FutureResponse(
        instrument.futureSpecification().productCode(),
        Long.toString(instrument.futureSpecification().contractMultiplierCent()));
    OptionResponse option = instrument.optionSpecification() == null ? null : new OptionResponse(
        instrument.optionSpecification().underlyingInstrumentId(), instrument.optionSpecification().optionRight(),
        Long.toString(instrument.optionSpecification().strikePriceCent()),
        Long.toString(instrument.optionSpecification().contractMultiplier()));
    return new InstrumentResponse(instrument.instrumentId(), instrument.market(), instrument.exchange(),
        instrument.symbol(), instrument.displayName(), instrument.assetType(), instrument.nativeCurrency(),
        instrument.maturityDate(), instrument.status().name(), future, option, instrument.tushareCode(),
        instrument.underlyingInstrumentId());
  }

  private static String canonical(CreateInstrumentRequest request) {
    String future = request.future() == null ? "null" : request.future().productCode() + "|"
        + request.future().contractMultiplierCent();
    String option = request.option() == null ? "null" : request.option().underlyingInstrumentId() + "|"
        + request.option().optionRight() + "|" + request.option().strikePriceCent() + "|"
        + request.option().contractMultiplier();
    return request.market().length() + ":" + request.market() + "|" + request.exchange().length() + ":"
        + request.exchange() + "|" + request.symbol().length() + ":" + request.symbol() + "|"
        + request.displayName().length() + ":" + request.displayName() + "|" + request.assetType() + "|"
        + request.nativeCurrency() + "|" + request.maturityDate() + "|" + future + "|" + option + "|"
        + request.tushareCode() + "|" + request.underlyingInstrumentId();
  }

  private static String ownerUserId(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (!(principal instanceof SessionAuthenticationPrincipal sessionPrincipal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return sessionPrincipal.user().userId();
  }

  public record CreateInstrumentRequest(
      @NotBlank @Size(max = 32) String market,
      @NotBlank @Size(max = 32) String exchange,
      @NotBlank @Size(max = 64) String symbol,
      @NotBlank @Size(max = 256) String displayName,
      @NotNull AssetType assetType,
      @NotNull CurrencyCode nativeCurrency,
      LocalDate maturityDate,
      @Valid FutureRequest future,
      @Valid OptionRequest option,
      @Size(max = 64) String tushareCode,
      @Size(max = 26) String underlyingInstrumentId) {
  }

  public record FutureRequest(@NotBlank String productCode,
                              @JsonDeserialize(using = StrictStringDeserializer.class)
                              @NotBlank String contractMultiplierCent) {
  }

  public record OptionRequest(@NotBlank String underlyingInstrumentId, @NotNull OptionRight optionRight,
                              @JsonDeserialize(using = StrictStringDeserializer.class)
                              @NotBlank String strikePriceCent,
                              @JsonDeserialize(using = StrictStringDeserializer.class)
                              @NotBlank String contractMultiplier) {
  }

  public record InstrumentResponse(String instrumentId, String market, String exchange, String symbol,
                                   String displayName, AssetType assetType, CurrencyCode nativeCurrency,
                                   LocalDate maturityDate, String status, FutureResponse future,
                                   OptionResponse option, String tushareCode, String underlyingInstrumentId) {
  }

  public record FutureResponse(String productCode, String contractMultiplierCent) {
  }

  public record OptionResponse(String underlyingInstrumentId, OptionRight optionRight, String strikePriceCent,
                               String contractMultiplier) {
  }

  @GetMapping
  public InstrumentPageResponse list(@RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) String cursor) {
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    var values = instrumentService.list().stream()
        .filter(instrument -> cursor == null || instrument.instrumentId().compareTo(cursor) > 0)
        .limit(limit + 1L).toList();
    boolean hasMore = values.size() > limit;
    var items = values.subList(0, Math.min(values.size(), limit)).stream().map(InstrumentController::response).toList();
    String nextCursor = hasMore ? items.get(items.size() - 1).instrumentId() : null;
    return new InstrumentPageResponse(items, nextCursor);
  }

  @PutMapping("/{instrumentId}/source-codes/TUSHARE_PRO")
  public InstrumentResponse updateTushareCode(@PathVariable @NotBlank String instrumentId,
      @Valid @RequestBody TushareCodeRequest request) {
    return response(instrumentService.updateTushareCode(instrumentId, request.tushareCode()));
  }

  public record TushareCodeRequest(@NotBlank @Size(max = 64) String tushareCode) {
  }

  public record InstrumentPageResponse(java.util.List<InstrumentResponse> items, String nextCursor) {
  }
}
