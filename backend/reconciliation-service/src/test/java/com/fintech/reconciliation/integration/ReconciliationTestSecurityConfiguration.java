package com.fintech.reconciliation.integration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@TestConfiguration(proxyBeanMethods = false)
public class ReconciliationTestSecurityConfiguration {

    private static final String ISSUER = "http://localhost:8180/realms/money-transfer";

    @Bean
    @Primary
    public JwtDecoder reconciliationTestJwtDecoder() {

        return token -> {
            Instant now = Instant.now();

            Jwt.Builder builder = Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .claim("iss", ISSUER)
                    .claim("aud", List.of("reconciliation-service"))
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(300));

            return switch (token) {

                case "operations-test-token" -> builder
                        .claim("azp", "money-transfer-web")
                        .claim("realm_access", Map.of(
                                "roles", List.of("OPERATIONS")))
                        .build();

                case "admin-test-token" -> builder
                        .claim("azp", "money-transfer-web")
                        .claim("realm_access", Map.of(
                                "roles", List.of("ADMIN")))
                        .build();

                case "customer-test-token" -> builder
                        .claim("azp", "money-transfer-web")
                        .claim("realm_access", Map.of(
                                "roles", List.of("CUSTOMER")))
                        .build();

                case "foreign-operations-test-token" -> builder
                        .claim("azp", "another-client")
                        .claim("realm_access", Map.of(
                                "roles", List.of("OPERATIONS")))
                        .build();

                default -> throw new JwtException(
                        "Unknown test token");
            };
        };
    }
}