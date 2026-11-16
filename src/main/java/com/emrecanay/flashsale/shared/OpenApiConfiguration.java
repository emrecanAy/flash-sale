package com.emrecanay.flashsale.shared;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI flashSaleApi() {
        return new OpenAPI().info(new Info()
                .title("Flash Sale API")
                .version("v1")
                .description("Order service for limited-stock product drops."));
    }
}
