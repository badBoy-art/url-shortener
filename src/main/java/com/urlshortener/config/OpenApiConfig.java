package com.urlshortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("短链服务 API")
                .version("1.0.0")
                .description("高可用高并发短链生成系统。管理接口（删除、全局统计）需携带 X-API-Key 请求头。"));
    }
}
