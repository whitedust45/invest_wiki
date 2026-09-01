package com.personal.investment;

import com.personal.investment.bootstrap.config.AuthProperties;
import com.personal.investment.bootstrap.config.ObjectStorageProperties;
import com.personal.investment.bootstrap.config.XxlJobProperties;
import com.personal.investment.bootstrap.config.TushareProProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan(basePackages = "com.personal.investment", annotationClass = Mapper.class)
@EnableConfigurationProperties({AuthProperties.class, ObjectStorageProperties.class, XxlJobProperties.class,
    TushareProProperties.class})
@EnableScheduling
public class InvestmentApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(InvestmentApiApplication.class, args);
  }
}
