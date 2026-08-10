package com.citics.glxt.contractchange.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/** Swagger 2 接口文档配置，仅扫描合同变更识别控制器包。 */
@Configuration
@EnableSwagger2
public class SwaggerConfig {
    /** 创建合同变更识别接口文档分组。 */
    @Bean
    public Docket contractChangeApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.citics.glxt.contractchange.controller"))
                .paths(PathSelectors.any())
                .build();
    }

    /** 定义第一版接口文档的标题、职责和版本。 */
    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("合同段落变更类型识别服务")
                .description("历史段落导入、语义检索和多标签变更类型推荐接口")
                .version("1.0.0")
                .build();
    }
}
