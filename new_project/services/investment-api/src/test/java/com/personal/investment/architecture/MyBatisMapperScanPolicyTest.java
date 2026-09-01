package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.personal.investment.InvestmentApiApplication;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

class MyBatisMapperScanPolicyTest {
  @Test
  void scansOnlyExplicitMyBatisMapperInterfaces() {
    MapperScan mapperScan = InvestmentApiApplication.class.getAnnotation(MapperScan.class);

    assertThat(mapperScan).isNotNull();
    assertThat(mapperScan.annotationClass()).isEqualTo(Mapper.class);
  }
}
