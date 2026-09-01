package com.personal.investment.bootstrap.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the executor only after an operator explicitly enables and configures it. */
@Configuration
@ConditionalOnProperty(prefix = "app.xxl-job", name = "enabled", havingValue = "true")
public class XxlJobConfiguration {
  @Bean
  XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
    XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
    executor.setEnabled(true);
    executor.setAdminAddresses(properties.adminAddresses());
    executor.setAccessToken(properties.accessToken());
    executor.setTimeout(properties.timeoutSeconds());
    executor.setAppname(properties.appName());
    executor.setAddress(properties.address());
    executor.setPort(properties.port());
    executor.setLogPath(properties.logPath());
    executor.setLogRetentionDays(properties.logRetentionDays());
    return executor;
  }
}
