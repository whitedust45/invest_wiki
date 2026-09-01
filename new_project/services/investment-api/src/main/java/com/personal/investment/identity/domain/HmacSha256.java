package com.personal.investment.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacSha256 {
  private HmacSha256() {
  }

  public static byte[] bytes(String key, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC 初始化失败", exception);
    }
  }

  public static String hex(String key, String value) {
    StringBuilder builder = new StringBuilder(64);
    for (byte item : bytes(key, value)) {
      builder.append(String.format("%02x", item));
    }
    return builder.toString();
  }
}
