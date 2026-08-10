package com.citics.glxt.contractchange;

import com.citics.glxt.contractchange.config.ContractChangeProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** 合同段落变更类型识别服务启动入口。 */
@SpringBootApplication
@MapperScan("com.citics.glxt.contractchange.mapper")
@EnableConfigurationProperties(ContractChangeProperties.class)
public class ContractChangeApplication {
    /** 启动 Spring Boot 应用。 */
    public static void main(String[] args) {
        SpringApplication.run(ContractChangeApplication.class, args);
    }
}
