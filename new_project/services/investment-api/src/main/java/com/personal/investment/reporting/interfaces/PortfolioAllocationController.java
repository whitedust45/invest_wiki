package com.personal.investment.reporting.interfaces;

import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.reporting.application.PortfolioAllocation;
import com.personal.investment.reporting.application.PortfolioAllocationService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/allocation")
public class PortfolioAllocationController {
  private final PortfolioAllocationService service;
  private final Clock clock;

  public PortfolioAllocationController(PortfolioAllocationService service, Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @GetMapping
  public PortfolioAllocationResponse allocation(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
      Authentication authentication) {
    return new PortfolioAllocationResponse(service.allocation(ownerUserId(authentication),
        asOf == null ? LocalDate.now(clock) : asOf).stream().map(PortfolioAllocationController::response).toList());
  }

  private static PortfolioAllocationCurrencyResponse response(PortfolioAllocation value) {
    return new PortfolioAllocationCurrencyResponse(value.currency().name(), value.valuationStatus().name(),
        value.marketValueCent() == null ? null : Long.toString(value.marketValueCent()), value.slices().stream()
            .map(slice -> new PortfolioAllocationSliceResponse(slice.instrumentId(), Long.toString(slice.marketValueCent()),
                slice.shareBasisPoints())).toList());
  }

  private static String ownerUserId(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof SessionAuthenticationPrincipal principal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return principal.user().userId();
  }

  public record PortfolioAllocationResponse(List<PortfolioAllocationCurrencyResponse> items) {
    public PortfolioAllocationResponse {
      items = List.copyOf(items);
    }
  }

  public record PortfolioAllocationCurrencyResponse(String currency, String valuationStatus, String marketValueCent,
                                                    List<PortfolioAllocationSliceResponse> slices) {
    public PortfolioAllocationCurrencyResponse {
      slices = List.copyOf(slices);
    }
  }

  public record PortfolioAllocationSliceResponse(String instrumentId, String marketValueCent, int shareBasisPoints) {
  }
}
