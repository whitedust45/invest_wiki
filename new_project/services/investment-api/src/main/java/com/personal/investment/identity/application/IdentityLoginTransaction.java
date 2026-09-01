package com.personal.investment.identity.application;

import com.personal.investment.bootstrap.config.AuthProperties;
import com.personal.investment.identity.domain.AuthenticatedUser;
import com.personal.investment.identity.domain.Role;
import com.personal.investment.identity.domain.UlidGenerator;
import com.personal.investment.identity.infrastructure.IdentityMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityLoginTransaction {
  private final IdentityMapper identityMapper;
  private final AuthProperties properties;

  public IdentityLoginTransaction(IdentityMapper identityMapper, AuthProperties properties) {
    this.identityMapper = identityMapper;
    this.properties = properties;
  }

  @Transactional(noRollbackFor = AuthException.class)
  public AuthenticatedUser authenticate(byte[] openidHmac, String bootstrapEnrollmentSecret,
      String traceId) {
    var existingUser = identityMapper.findActiveUserByOpenIdHmac(openidHmac);
    if (existingUser.isPresent()) {
      identityMapper.insertLoginAudit(UlidGenerator.next(), existingUser.get().userId(), null,
          "SUCCEEDED", traceId, null);
      return new AuthenticatedUser(existingUser.get().userId(), Role.ADMIN,
          existingUser.get().permissionVersion());
    }

    if (identityMapper.acquireBootstrapLock() != 1) {
      throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
          "管理员初始化锁暂不可用");
    }

    try {
      if (identityMapper.countActiveAdministrators() > 0) {
        reject(traceId, "NOT_ADMIN", HttpStatus.FORBIDDEN, "当前微信身份不在管理员范围内");
      }
      if (!matchesBootstrapSecret(bootstrapEnrollmentSecret)) {
        reject(traceId, "BOOTSTRAP_SECRET_INVALID", HttpStatus.BAD_REQUEST, "初始化密钥无效");
      }

      String userId = UlidGenerator.next();
      String wechatIdentityId = UlidGenerator.next();
      identityMapper.insertUser(userId);
      identityMapper.insertWechatIdentity(wechatIdentityId, userId, openidHmac);
      identityMapper.insertLoginAudit(UlidGenerator.next(), userId, wechatIdentityId, "SUCCEEDED",
          traceId, null);
      return new AuthenticatedUser(userId, com.personal.investment.identity.domain.Role.ADMIN, 0);
    } finally {
      identityMapper.releaseBootstrapLock();
    }
  }

  private boolean matchesBootstrapSecret(String candidate) {
    if (candidate == null) {
      return false;
    }
    return MessageDigest.isEqual(properties.bootstrapEnrollmentSecret().getBytes(StandardCharsets.UTF_8),
        candidate.getBytes(StandardCharsets.UTF_8));
  }

  private void reject(String traceId, String code, HttpStatus status, String message) {
    identityMapper.insertLoginAudit(UlidGenerator.next(), null, null, "REJECTED", traceId, code);
    throw new AuthException(status, code, message);
  }
}
