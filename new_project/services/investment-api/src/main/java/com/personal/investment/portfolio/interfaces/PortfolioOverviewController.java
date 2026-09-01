package com.personal.investment.portfolio.interfaces;

import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.portfolio.application.PortfolioCurrencyOverview;
import com.personal.investment.portfolio.application.PortfolioOverview;
import com.personal.investment.portfolio.application.PortfolioOverviewService;
import com.personal.investment.portfolio.application.PortfolioPositionView;
import com.personal.investment.portfolio.application.PortfolioValuationStatus;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioOverviewController {
  private final PortfolioOverviewService overviewService;
  private final Clock clock;

  public PortfolioOverviewController(PortfolioOverviewService overviewService, Clock clock) {
    this.overviewService = overviewService;
    this.clock = clock;
  }

  @GetMapping("/summary")
  public PortfolioSummaryResponse summary(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
      Authentication authentication) {
    PortfolioOverview overview = overviewService.summary(ownerUserId(authentication), effectiveAsOf(asOf));
    return new PortfolioSummaryResponse(overview.items().stream().map(PortfolioOverviewController::response).toList());
  }

  @GetMapping("/positions")
  public PortfolioCurrencyResponse positions(@RequestParam CurrencyCode currency,
      @RequestParam(required = false) @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String accountId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
      Authentication authentication) {
    return response(overviewService.positions(ownerUserId(authentication), currency, accountId, effectiveAsOf(asOf)));
  }

  private LocalDate effectiveAsOf(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }

  private static PortfolioCurrencyResponse response(PortfolioCurrencyOverview overview) {
    return new PortfolioCurrencyResponse(overview.currency(), Long.toString(overview.cashCent()),
        Long.toString(overview.marginCent()), decimal(overview.marketValueCent()), decimal(overview.netAssetCent()),
        overview.positions().stream().map(PortfolioOverviewController::positionResponse).toList(), overview.asOf(),
        Long.toString(overview.sourceLedgerVersion()), overview.valuationStatus());
  }

  private static PortfolioPositionResponse positionResponse(PortfolioPositionView position) {
    return new PortfolioPositionResponse(position.cashAccountId(), position.instrumentId(), position.currency(),
        position.quantity().toPlainString(), decimal(position.marketValueCent()), decimal(position.costCent()),
        decimal(position.unrealizedPnlCent()), position.valuationStatus().name());
  }

  private static String decimal(Long value) {
    return value == null ? null : Long.toString(value);
  }

  private static String ownerUserId(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof SessionAuthenticationPrincipal principal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return principal.user().userId();
  }

  public record PortfolioSummaryResponse(List<PortfolioCurrencyResponse> items) {
  }

  public record PortfolioCurrencyResponse(CurrencyCode currency, String cashCent, String marginCent,
                                          String marketValueCent, String netAssetCent,
                                          List<PortfolioPositionResponse> positions, LocalDate asOf,
                                          String sourceLedgerVersion, PortfolioValuationStatus valuationStatus) {
  }

  public record PortfolioPositionResponse(String cashAccountId, String instrumentId, CurrencyCode currency,
                                          String quantity, String marketValueCent, String costCent,
                                          String unrealizedPnlCent, String valuationStatus) {
  }
}
