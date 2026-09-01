package com.personal.investment.identity.application;

import com.personal.investment.bootstrap.config.AuthProperties;
import com.personal.investment.identity.domain.HmacSha256;
import com.personal.investment.identity.infrastructure.RedisSessionStore;
import org.springframework.stereotype.Service;

@Service
public class LoginService {
  private final WeChatIdentityPort weChatIdentityPort;
  private final IdentityLoginTransaction identityLoginTransaction;
  private final RedisSessionStore sessionStore;
  private final AuthProperties properties;

  public LoginService(WeChatIdentityPort weChatIdentityPort,
      IdentityLoginTransaction identityLoginTransaction, RedisSessionStore sessionStore,
      AuthProperties properties) {
    this.weChatIdentityPort = weChatIdentityPort;
    this.identityLoginTransaction = identityLoginTransaction;
    this.sessionStore = sessionStore;
    this.properties = properties;
  }

  public LoginResult login(String code, String bootstrapEnrollmentSecret, String traceId) {
    String openId = weChatIdentityPort.exchangeCode(code);
    var user = identityLoginTransaction.authenticate(
        HmacSha256.bytes(properties.openidHmacKey(), openId), bootstrapEnrollmentSecret, traceId);
    var session = sessionStore.create(user);
    return new LoginResult(session.token(), session.expiresAt(), user.userId(), user.role());
  }

  public record LoginResult(String accessToken, java.time.Instant expiresAt, String userId,
      com.personal.investment.identity.domain.Role role) {
  }
}
