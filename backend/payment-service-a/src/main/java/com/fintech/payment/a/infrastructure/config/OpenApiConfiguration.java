package com.fintech.payment.a.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    public OpenAPI paymentAOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fault-Tolerant Money Transfer — Bank A API")
                        .version("v1")
                        .description("""
                                REST API for customer-owned Bank A accounts,
                                internal account-to-account transfers,
                                and account ledger queries.
                                """))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("bearerAuth"));
    }
}
