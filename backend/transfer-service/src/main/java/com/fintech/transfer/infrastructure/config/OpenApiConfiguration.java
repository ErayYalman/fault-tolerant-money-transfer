package com.fintech.transfer.infrastructure.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI transferServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fault-Tolerant Distributed Money Transfer System API")
                        .version("v1")
                        .description("""
                                REST API for the transfer-service.

                                This service manages transfer lifecycle information
                                and acts as the Saga orchestration entry point.
                                """)
                        .contact(new Contact()
                                .name("Eray Yalman")))
                .externalDocs(new ExternalDocumentation()
                        .description("Project Documentation"));
    }
}