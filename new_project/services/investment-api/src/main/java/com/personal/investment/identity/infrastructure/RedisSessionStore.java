package com.personal.investment.identity.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.bootstrap.config.AuthProperties;
import com.personal.investment.identity.application.AuthException;
import com.personal.investment.identity.domain.AuthenticatedUser;
import com.personal.investment.identity.domain.HmacSha256;
import com.personal.investment.identity.domain.Role;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RedisSessionStore {
  private static final String KEY_PREFIX = "investment:session:";
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final AuthProperties properties;
  private final SecureRandom random = new SecureRandom();

  public RedisSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
      AuthProperties properties) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  public CreatedSession create(AuthenticatedUser user) {
    byte[] rawToken = new byte[32];
    random.nextBytes(rawToken);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken);
    Instant expiresAt = Instant.now().plus(properties.sessionTtl());
    StoredSession session = new StoredSession(user.userId(), user.role(), user.permissionVersion(),
        expiresAt);
    try {
      redisTemplate.opsForValue().set(key(token), objectMapper.writeValueAsString(session),
          properties.sessionTtl());
      return new CreatedSession(token, expiresAt);
    } catch (RedisConnectionFailureException | JsonProcessingException exception) {
      throw unavailable(exception);
    }
  }

  public Optional<StoredSession> find(String token) {
    try {
      String content = redisTemplate.opsForValue().get(key(token));
      if (content == null) {
        return Optional.empty();
      }
      StoredSession session = objectMapper.readValue(content, StoredSession.class);
      if (!session.expiresAt().isAfter(Instant.now())) {
        redisTemplate.delete(key(token));
        return Optional.empty();
      }
      return Optional.of(session);
    } catch (RedisConnectionFailureException | JsonProcessingException exception) {
      throw unavailable(exception);
    }
  }

  private String key(String token) {
    return KEY_PREFIX + HmacSha256.hex(properties.sessionHmacKey(), token);
  }

  private AuthException unavailable(Exception cause) {
    return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SESSION_STORE_UNAVAILABLE",
        "会话服务暂不可用");
  }

  public record CreatedSession(String token, Instant expiresAt) {
  }

  public record StoredSession(String userId, Role role, long permissionVersion, Instant expiresAt) {
  }
}
