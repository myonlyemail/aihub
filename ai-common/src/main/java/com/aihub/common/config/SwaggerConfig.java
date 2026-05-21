package com.aihub.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AIHub API")
                        .description("AIHub 智能体聚合平台 API 文档")
                        .version("1.0.0")
                        .contact(new Contact().name("AIHub Team").email("team@aihub.com"))
                        .license(new License().name("MIT")));
    }
}
