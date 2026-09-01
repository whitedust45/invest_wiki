package com.personal.investment.identity.infrastructure;

import com.personal.investment.identity.application.AuthException;
import com.personal.investment.identity.application.WeChatIdentityPort;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class UnavailableWeChatIdentityAdapter implements WeChatIdentityPort {
  @Override
  public String exchangeCode(String code) {
    throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_AUTH_UNAVAILABLE",
        "真实微信认证适配器尚未配置");
  }
}
