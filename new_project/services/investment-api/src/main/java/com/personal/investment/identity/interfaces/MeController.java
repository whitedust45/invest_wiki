package com.personal.investment.identity.interfaces;

import com.personal.investment.identity.domain.AuthenticatedUser;
import com.personal.investment.identity.domain.Role;
import com.personal.investment.identity.infrastructure.RedisSessionStore;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeController {
  @GetMapping("/me")
  public MeResponse me(Authentication authentication) {
    SessionAuthenticationPrincipal principal = (SessionAuthenticationPrincipal) authentication.getPrincipal();
    return new MeResponse(principal.user().userId(), principal.user().role(), principal.expiresAt());
  }

  public record MeResponse(String userId, Role role, Instant sessionExpiresAt) {
  }

  public record SessionAuthenticationPrincipal(AuthenticatedUser user, Instant expiresAt) {
    public static SessionAuthenticationPrincipal from(RedisSessionStore.StoredSession session) {
      return new SessionAuthenticationPrincipal(
          new AuthenticatedUser(session.userId(), session.role(), session.permissionVersion()),
          session.expiresAt());
    }
  }
}
