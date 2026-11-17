
package com.fintech.payment.a.support;

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
public class PaymentATestSecurityConfiguration {

    private static final String ISSUER = "http://localhost:8180/realms/money-transfer";

    @Bean
    @Primary
    public JwtDecoder paymentATestJwtDecoder() {
        return token -> {
            Instant now = Instant.now();

            Jwt.Builder builder = Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .claim("iss", ISSUER)
                    .claim("aud", List.of("payment-service-a"))
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(300));

            return switch (token) {
                case "reconciliation-test-token" -> builder
                        .claim("azp", "reconciliation-service")
                        .claim("resource_access", Map.of(
                                "payment-service-a", Map.of(
                                        "roles", List.of("reconciliation-read"))))
                        .build();

                case "customer-test-token" -> builder
                        .claim("azp", "money-transfer-web")
                        .claim("realm_access", Map.of(
                                "roles", List.of("CUSTOMER")))
                        .build();

                case "customer-a-test-token" -> builder
                        .claim("sub", "customer-a-subject")
                        .claim("azp", "money-transfer-web")
                        .claim("realm_access", Map.of(
                                "roles", List.of("CUSTOMER")))
                        .build();

                case "customer-b-test-token" -> builder
                        .claim("sub", "customer-b-subject")
                        .claim("azp", "money-transfer-web")
                        .claim("realm_access", Map.of(
                                "roles", List.of("CUSTOMER")))
                        .build();

                case "customer-empty-test-token" -> builder
                        .claim("sub", "customer-empty-subject")
                        .claim("azp", "money-transfer-web")
                        .claim("realm_access", Map.of(
                                "roles", List.of("CUSTOMER")))
                        .build();

                case "customer-no-role-test-token" -> builder
                        .claim("sub", "customer-no-role-subject")
                        .claim("azp", "money-transfer-web")
                        .claim("realm_access", Map.of(
                                "roles", List.of("OPERATIONS")))
                        .build();

                case "foreign-client-test-token" -> builder
                        .claim("azp", "another-client")
                        .claim("resource_access", Map.of(
                                "payment-service-a", Map.of(
                                        "roles", List.of("reconciliation-read"))))
                        .build();

                case "reconciliation-without-role-token" -> builder
                        .claim("azp", "reconciliation-service")
                        .claim("resource_access", Map.of(
                                "payment-service-a", Map.of(
                                        "roles", List.of())))
                        .build();

                default -> throw new JwtException("Unknown test token");
            };
        };
    }
}
