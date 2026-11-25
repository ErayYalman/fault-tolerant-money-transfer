package com.fintech.reconciliation.infrastructure.config;

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
    public OpenAPI reconciliationServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fault-Tolerant Money Transfer — Reconciliation API")
                        .version("v1")
                        .description("""
                                API documentation for the reconciliation-service.

                                This service executes T+1 reconciliation,
                                exposes reconciliation run details,
                                and provides statistical anomaly scoring results.
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
