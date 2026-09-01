package com.personal.investment.reporting.interfaces;

import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.reporting.application.PortfolioHistoryPoint;
import com.personal.investment.reporting.application.PortfolioHistoryService;
import java.time.LocalDate;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/reports/portfolio-history")
public class PortfolioHistoryController {
  private final PortfolioHistoryService service;
  private final Clock clock;

  public PortfolioHistoryController(PortfolioHistoryService service, Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @PostMapping
  public ResponseEntity<SnapshotWriteResponse> snapshot(@RequestParam(required = false) LocalDate asOfDate,
      Authentication authentication) {
    var result = service.snapshot(ownerUserId(authentication), asOfDate == null ? LocalDate.now(clock) : asOfDate);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new SnapshotWriteResponse(result.persistedCount(), result.skippedUnvaluedCurrencyCount()));
  }

  @GetMapping
  public PortfolioHistoryResponse list(@RequestParam CurrencyCode currency, @RequestParam LocalDate from,
      @RequestParam LocalDate to, @RequestParam(defaultValue = "366") int limit, Authentication authentication) {
    List<PortfolioHistoryPoint> items = service.list(ownerUserId(authentication), currency, from, to, limit);
    return new PortfolioHistoryResponse(items.stream().map(PortfolioHistoryController::response).toList(),
        service.chart(items).stream().map(point -> new PortfolioHistoryChartPointResponse(point.dailySnapshotId(),
            point.asOfDate(), point.netAssetBasisPoints())).toList());
  }

  private static PortfolioHistoryPointResponse response(PortfolioHistoryPoint point) {
    return new PortfolioHistoryPointResponse(point.dailySnapshotId(), point.currency().name(), point.asOfDate(),
        Long.toString(point.netAssetCent()), Long.toString(point.cashCent()), Long.toString(point.marketValueCent()),
        Long.toString(point.sourceLedgerVersion()), point.calculatedAt());
  }

  private static String ownerUserId(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (!(principal instanceof SessionAuthenticationPrincipal value)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return value.user().userId();
  }

  public record SnapshotWriteResponse(int persistedCount, int skippedUnvaluedCurrencyCount) {
  }

  public record PortfolioHistoryResponse(List<PortfolioHistoryPointResponse> items,
                                         List<PortfolioHistoryChartPointResponse> chartPoints) {
    public PortfolioHistoryResponse {
      items = List.copyOf(items);
      chartPoints = List.copyOf(chartPoints);
    }
  }

  public record PortfolioHistoryPointResponse(String dailySnapshotId, String currency, LocalDate asOfDate,
                                              String netAssetCent, String cashCent, String marketValueCent,
                                              String sourceLedgerVersion, java.time.Instant calculatedAt) {
  }

  public record PortfolioHistoryChartPointResponse(String dailySnapshotId, LocalDate asOfDate,
                                                   int netAssetBasisPoints) {
  }
}
