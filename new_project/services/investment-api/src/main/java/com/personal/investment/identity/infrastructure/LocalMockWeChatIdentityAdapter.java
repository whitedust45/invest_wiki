package com.personal.investment.identity.infrastructure;

import com.personal.investment.bootstrap.config.AuthProperties;
import com.personal.investment.identity.application.AuthException;
import com.personal.investment.identity.application.WeChatIdentityPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalMockWeChatIdentityAdapter implements WeChatIdentityPort {
  private final AuthProperties properties;

  public LocalMockWeChatIdentityAdapter(AuthProperties properties) {
    this.properties = properties;
  }

  @Override
  public String exchangeCode(String code) {
    if (!properties.mockEnabled()
        || properties.mockLoginCode() == null
        || properties.mockOpenId() == null
        || !MessageDigest.isEqual(properties.mockLoginCode().getBytes(StandardCharsets.UTF_8),
            code.getBytes(StandardCharsets.UTF_8))) {
      throw new AuthException(HttpStatus.BAD_REQUEST, "AUTH_CODE_INVALID", "登录 code 无效");
    }
    return properties.mockOpenId();
  }
}
