package com.personal.investment.strategy.interfaces;

import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.strategy.application.LocalStrategyTestSeedService;
import com.personal.investment.strategy.application.StrategySeedResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately absent outside the local Spring profile. The fixture is one-shot per owner and never a production
 * data-loading mechanism.
 */
@Validated
@RestController
@Profile("local")
@RequestMapping("/api/v1/development")
public class LocalStrategyTestSeedController {
  private final LocalStrategyTestSeedService seedService;

  public LocalStrategyTestSeedController(LocalStrategyTestSeedService seedService) {
    this.seedService = seedService;
  }

  @PostMapping("/strategy-test-seed")
  public ResponseEntity<StrategySeedResponse> seed(@Valid @RequestBody StrategySeedRequest request,
      Authentication authentication) {
    StrategySeedResult result = seedService.seed(ownerUserId(authentication));
    return ResponseEntity.status(HttpStatus.CREATED).body(new StrategySeedResponse(result.seedName(),
        result.createdCashAccounts(), result.createdInstruments(), result.createdTransactions(),
        result.createdEvaluations(), result.currencies()));
  }

  private static String ownerUserId(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof SessionAuthenticationPrincipal principal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return principal.user().userId();
  }

  public record StrategySeedRequest(
      @NotBlank @Pattern(regexp = "LEGACY_FULL_PATH") String seedSet) {
  }

  public record StrategySeedResponse(String seedName, int createdAccounts, int createdInstruments,
                                     int createdTransactions, int createdEvaluations, List<String> currencies) {
    public StrategySeedResponse {
      currencies = List.copyOf(currencies);
    }
  }
}
