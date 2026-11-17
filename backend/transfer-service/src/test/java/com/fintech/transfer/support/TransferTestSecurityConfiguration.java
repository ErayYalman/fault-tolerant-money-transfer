package com.fintech.transfer.support;

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
public class TransferTestSecurityConfiguration {

        private static final String ISSUER = "http://localhost:8180/realms/money-transfer";

        @Bean
        @Primary
        public JwtDecoder transferTestJwtDecoder() {
                return token -> {
                        Instant now = Instant.now();

                        Jwt.Builder builder = Jwt.withTokenValue(token)
                                        .header("alg", "RS256")
                                        .claim("iss", ISSUER)
                                        .claim("aud", List.of("transfer-service"))
                                        .issuedAt(now)
                                        .expiresAt(now.plusSeconds(300));

                        return switch (token) {
                                case "customer-test-token" -> builder
                                                .subject("customer-user-1")
                                                .claim("azp", "money-transfer-web")
                                                .claim("realm_access", Map.of(
                                                                "roles", List.of("CUSTOMER")))
                                                .build();

                                case "another-customer-test-token" -> builder
                                                .subject("customer-user-2")
                                                .claim("azp", "money-transfer-web")
                                                .claim("realm_access", Map.of(
                                                                "roles", List.of("CUSTOMER")))
                                                .build();

                                case "operations-test-token" -> builder
                                                .subject("operations-user-1")
                                                .claim("azp", "money-transfer-web")
                                                .claim("realm_access", Map.of(
                                                                "roles", List.of("OPERATIONS")))
                                                .build();

                                case "reconciliation-test-token" -> builder
                                                .claim("azp", "reconciliation-service")
                                                .claim("resource_access", Map.of(
                                                                "transfer-service", Map.of(
                                                                                "roles",
                                                                                List.of("reconciliation-read"))))
                                                .build();

                                case "foreign-client-test-token" -> builder
                                                .claim("azp", "another-client")
                                                .claim("resource_access", Map.of(
                                                                "transfer-service", Map.of(
                                                                                "roles",
                                                                                List.of("reconciliation-read"))))
                                                .build();

                                case "foreign-customer-test-token" -> builder
                                                .subject("foreign-client-user")
                                                .claim("azp", "another-client")
                                                .claim("realm_access", Map.of(
                                                                "roles", List.of("CUSTOMER")))
                                                .build();

                                default -> throw new JwtException(
                                                "Unknown test token");
                        };
                };
        }
}