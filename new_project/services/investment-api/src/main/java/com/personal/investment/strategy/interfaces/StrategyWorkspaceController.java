package com.personal.investment.strategy.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.interfaces.StrictStringDeserializer;
import com.personal.investment.strategy.application.StrategyCard;
import com.personal.investment.strategy.application.StrategyEvaluation;
import com.personal.investment.strategy.application.StrategyEvaluationService;
import com.personal.investment.strategy.application.StrategyHistoryService;
import com.personal.investment.strategy.application.StrategyValidationCode;
import com.personal.investment.strategy.application.StrategyValidationException;
import com.personal.investment.strategy.application.StrategyReferenceNav;
import com.personal.investment.strategy.application.StrategyReferenceNavService;
import com.personal.investment.strategy.application.StrategyRuleVersion;
import com.personal.investment.strategy.application.StrategyRuleVersionService;
import com.personal.investment.strategy.application.StrategyScan;
import com.personal.investment.strategy.application.StrategyScanItem;
import com.personal.investment.strategy.application.StrategyScanService;
import com.personal.investment.strategy.application.StrategyWorkspaceService;
import com.personal.investment.strategy.domain.StrategyKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyWorkspaceController {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final StrategyWorkspaceService workspaceService;
  private final StrategyRuleVersionService ruleVersionService;
  private final StrategyReferenceNavService referenceNavService;
  private final StrategyEvaluationService evaluationService;
  private final StrategyHistoryService historyService;
  private final StrategyScanService scanService;

  public StrategyWorkspaceController(StrategyWorkspaceService workspaceService,
      StrategyRuleVersionService ruleVersionService, StrategyReferenceNavService referenceNavService,
      StrategyEvaluationService evaluationService, StrategyHistoryService historyService, StrategyScanService scanService) {
    this.workspaceService = workspaceService;
    this.ruleVersionService = ruleVersionService;
    this.referenceNavService = referenceNavService;
    this.evaluationService = evaluationService;
    this.historyService = historyService;
    this.scanService = scanService;
  }

  @GetMapping
  public StrategyListResponse list(Authentication authentication) {
    return new StrategyListResponse(workspaceService.listCards(ownerUserId(authentication)).stream()
        .map(StrategyWorkspaceController::card).toList());
  }

  @GetMapping("/{strategyKey}/workspace")
  public StrategyWorkspaceResponse workspace(@PathVariable String strategyKey, Authentication authentication) {
    var snapshot = workspaceService.workspace(ownerUserId(authentication), StrategyKey.from(strategyKey));
    StrategyCard card = snapshot.card();
    return new StrategyWorkspaceResponse(card(card), availableActions(card.strategyKey(), card.activeRuleVersionId()),
        snapshot.activeRule() == null ? null : new RuleVersionHistoryResponse(snapshot.activeRule().strategyRuleVersionId(),
            snapshot.activeRule().strategyKey().name(), snapshot.activeRule().ruleVersion(),
            parseResult(snapshot.activeRule().ruleJson()), snapshot.activeRule().status().name(),
            snapshot.activeRule().createdAt()), snapshot.latestEvaluation() == null ? null
                : evaluation(snapshot.latestEvaluation()));
  }

  @PostMapping("/{strategyKey}/rule-versions")
  public ResponseEntity<RuleVersionResponse> createRuleVersion(@PathVariable String strategyKey,
      @Valid @RequestBody RuleVersionRequest request, Authentication authentication) {
    StrategyRuleVersion value = ruleVersionService.create(ownerUserId(authentication), StrategyKey.from(strategyKey),
        request.ruleVersion(), request.rule(), request.expectedActiveRuleVersionId());
    return ResponseEntity.status(HttpStatus.CREATED).body(new RuleVersionResponse(value.strategyRuleVersionId(),
        value.strategyKey().name(), value.ruleVersion(), value.status().name(), value.createdAt()));
  }

  @GetMapping("/{strategyKey}/rule-versions")
  public RuleVersionPageResponse ruleVersions(@PathVariable String strategyKey,
      @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "30") int limit,
      Authentication authentication) {
    var page = historyService.ruleVersions(ownerUserId(authentication), StrategyKey.from(strategyKey), cursor, limit);
    return new RuleVersionPageResponse(page.items().stream().map(value -> new RuleVersionHistoryResponse(
        value.strategyRuleVersionId(), value.strategyKey().name(), value.ruleVersion(), parseResult(value.ruleJson()),
        value.status().name(), value.createdAt())).toList(), page.nextCursor());
  }

  @PostMapping("/{strategyKey}/reference-nav")
  public ResponseEntity<ReferenceNavResponse> recordReferenceNav(@PathVariable String strategyKey,
      @Valid @RequestBody ReferenceNavRequest request, Authentication authentication) {
    CurrencyCode currency = CurrencyCode.of(request.currency());
    StrategyReferenceNav value = referenceNavService.record(ownerUserId(authentication), StrategyKey.from(strategyKey),
        currency, parsePositiveCent(request.referenceNavCent()), request.asOfAt(), request.validUntil(), "MANUAL");
    return ResponseEntity.status(HttpStatus.CREATED).body(new ReferenceNavResponse(value.strategyReferenceNavId(),
        value.strategyKey().name(), value.currency().name(), Long.toString(value.referenceNavCent()), value.asOfAt(),
        value.validUntil(), value.source()));
  }

  @GetMapping("/{strategyKey}/reference-nav")
  public ReferenceNavPageResponse referenceNavs(@PathVariable String strategyKey,
      @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "30") int limit,
      Authentication authentication) {
    var page = historyService.referenceNavs(ownerUserId(authentication), StrategyKey.from(strategyKey), cursor, limit);
    return new ReferenceNavPageResponse(page.items().stream().map(value -> new ReferenceNavResponse(
        value.strategyReferenceNavId(), value.strategyKey().name(), value.currency().name(),
        Long.toString(value.referenceNavCent()), value.asOfAt(), value.validUntil(), value.source())).toList(),
        page.nextCursor());
  }

  @PostMapping("/{strategyKey}/evaluations")
  public StrategyEvaluationResponse evaluate(@PathVariable String strategyKey,
      @RequestBody(required = false) EvaluationRequest request, Authentication authentication) {
    Instant asOfAt = request == null || request.asOfDate() == null
        ? Instant.now() : request.asOfDate().atStartOfDay().toInstant(ZoneOffset.UTC);
    StrategyEvaluation value = evaluationService.evaluate(ownerUserId(authentication), StrategyKey.from(strategyKey),
        asOfAt);
    return evaluation(value);
  }

  @GetMapping("/{strategyKey}/evaluations")
  public EvaluationPageResponse evaluations(@PathVariable String strategyKey,
      @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "30") int limit,
      Authentication authentication) {
    var page = historyService.evaluations(ownerUserId(authentication), StrategyKey.from(strategyKey), cursor, limit);
    return new EvaluationPageResponse(page.items().stream().map(StrategyWorkspaceController::evaluation).toList(),
        page.nextCursor());
  }

  @PostMapping("/scans")
  public ResponseEntity<StrategyScanResponse> scan(@RequestBody(required = false) StrategyScanRequest request,
      Authentication authentication) {
    StrategyScan value = scanService.request(ownerUserId(authentication), request == null ? null : request.strategyKeys());
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(scan(value, List.of()));
  }

  @GetMapping("/scans/{strategyScanId}")
  public StrategyScanResponse scan(
      @PathVariable @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String strategyScanId,
      Authentication authentication) {
    String owner = ownerUserId(authentication);
    StrategyScan value = scanService.find(owner, strategyScanId);
    return scan(value, scanService.items(owner, strategyScanId));
  }

  private static StrategyCardResponse card(StrategyCard value) {
    return new StrategyCardResponse(value.strategyKey().name(), value.displayName(), value.currency().name(),
        value.activeRuleVersionId(), value.inputAt(), value.status().name(), value.message());
  }

  private static StrategyEvaluationResponse evaluation(StrategyEvaluation value) {
    return new StrategyEvaluationResponse(value.strategyEvaluationId(), value.strategyKey().name(),
        value.strategyRuleVersionId(), value.inputVersion(), value.asOfAt(), value.status().name(),
        parseResult(value.resultJson()));
  }

  private static List<String> availableActions(StrategyKey strategyKey, String activeRuleVersionId) {
    if (activeRuleVersionId == null) {
      return List.of("CREATE_RULE_VERSION", "CREATE_CASH_ACCOUNT", "RECORD_FUNDING", "CREATE_INSTRUMENT");
    }
    return switch (strategyKey) {
      case HIGH_DIVIDEND, QQQ_GROWTH -> List.of("RECORD_SPOT_TRADE", "RECORD_DIVIDEND", "VIEW_EVALUATIONS");
      case IC_IM -> List.of("RECORD_MARGIN", "RECORD_FUTURES", "ROLL_FUTURES", "VIEW_EVALUATIONS");
      case DEEP_PUT -> List.of("RECORD_OPTION", "RECORD_OPTION_EXPIRY", "VIEW_EVALUATIONS");
    };
  }

  private static StrategyScanResponse scan(StrategyScan value, List<StrategyScanItem> items) {
    return new StrategyScanResponse(value.strategyScanId(), value.asOfAt(), value.status().name(), value.attemptNo(),
        value.startedAt(), value.completedAt(), value.resultJson() == null ? null : parseResult(value.resultJson()),
        items.stream().map(item -> new StrategyScanItemResponse(item.strategyScanItemId(), item.strategyKey().name(),
            item.strategyEvaluationId(), item.status(), item.failureCode(), item.failureMessage(), item.createdAt())).toList());
  }

  private static long parsePositiveCent(String value) {
    if (value == null || !value.matches("[1-9][0-9]*")) {
      throw new StrategyValidationException(StrategyValidationCode.MONEY_CONVENTION_VIOLATION,
          "referenceNavCent must be a positive decimal integer string");
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new StrategyValidationException(StrategyValidationCode.MONEY_CONVENTION_VIOLATION,
          "referenceNavCent exceeds long range");
    }
  }

  private static JsonNode parseResult(String resultJson) {
    try {
      return JSON.readTree(resultJson);
    } catch (Exception exception) {
      throw new IllegalStateException("strategy evaluation result is not valid JSON", exception);
    }
  }

  private static String ownerUserId(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof SessionAuthenticationPrincipal principal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return principal.user().userId();
  }

  public record StrategyListResponse(List<StrategyCardResponse> items) {
  }

  public record StrategyCardResponse(String strategyKey, String displayName, String currency,
                                     String activeRuleVersionId, Instant inputAt, String status, String message) {
  }

  public record StrategyWorkspaceResponse(StrategyCardResponse strategy, List<String> availableActions,
                                          RuleVersionHistoryResponse activeRule,
                                          StrategyEvaluationResponse latestEvaluation) {
  }

  public record RuleVersionRequest(@NotBlank @Size(max = 64) String ruleVersion,
                                   @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String expectedActiveRuleVersionId,
                                   @NotNull JsonNode rule) {
  }

  public record RuleVersionResponse(String strategyRuleVersionId, String strategyKey, String ruleVersion,
                                    String status, Instant createdAt) {
  }

  public record RuleVersionHistoryResponse(String strategyRuleVersionId, String strategyKey, String ruleVersion,
                                           JsonNode rule, String status, Instant createdAt) {
  }

  public record RuleVersionPageResponse(List<RuleVersionHistoryResponse> items, String nextCursor) {
  }

  public record ReferenceNavRequest(@JsonDeserialize(using = StrictStringDeserializer.class) String referenceNavCent,
                                    @NotBlank @Pattern(regexp = "USD") String currency,
                                    @NotNull Instant asOfAt, @NotNull Instant validUntil) {
  }

  public record ReferenceNavResponse(String strategyReferenceNavId, String strategyKey, String currency,
                                     String referenceNavCent, Instant asOfAt, Instant validUntil, String source) {
  }

  public record ReferenceNavPageResponse(List<ReferenceNavResponse> items, String nextCursor) {
  }

  public record EvaluationRequest(LocalDate asOfDate) {
  }

  public record StrategyEvaluationResponse(String strategyEvaluationId, String strategyKey,
                                           String strategyRuleVersionId, String inputVersion, Instant asOfAt,
                                           String status, JsonNode result) {
  }

  public record EvaluationPageResponse(List<StrategyEvaluationResponse> items, String nextCursor) {
  }

  public record StrategyScanRequest(List<StrategyKey> strategyKeys) {
    public StrategyScanRequest {
      strategyKeys = strategyKeys == null ? List.of() : List.copyOf(strategyKeys);
    }
  }

  public record StrategyScanResponse(String strategyScanId, Instant asOfAt, String status, short attemptNo,
                                     Instant startedAt, Instant completedAt, JsonNode result,
                                     List<StrategyScanItemResponse> items) {
  }

  public record StrategyScanItemResponse(String strategyScanItemId, String strategyKey, String strategyEvaluationId,
                                         String status, String failureCode, String failureMessage, Instant createdAt) {
  }
}
