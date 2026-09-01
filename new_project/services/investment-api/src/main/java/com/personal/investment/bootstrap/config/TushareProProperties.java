package com.personal.investment.bootstrap.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Token is intentionally optional: absence degrades a run to audited DATA_STALE rather than preventing startup. */
@ConfigurationProperties(prefix = "app.market.tushare")
public record TushareProProperties(String token, URI endpoint, Duration connectTimeout, Duration requestTimeout) {
  public TushareProProperties {
    endpoint = endpoint == null ? URI.create("https://api.tushare.pro") : endpoint;
    connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
    requestTimeout = requestTimeout == null ? Duration.ofSeconds(20) : requestTimeout;
  }

  public boolean configured() {
    return token != null && !token.isBlank();
  }
}
