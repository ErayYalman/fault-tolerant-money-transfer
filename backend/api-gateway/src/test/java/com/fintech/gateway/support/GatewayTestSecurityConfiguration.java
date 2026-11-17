
package com.fintech.gateway.support;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import reactor.core.publisher.Mono;

@TestConfiguration(proxyBeanMethods = false)
public class GatewayTestSecurityConfiguration {

    private static final String ISSUER = "http://localhost:8180/realms/money-transfer";

    @Bean
    @Primary
    public ReactiveJwtDecoder gatewayTestJwtDecoder() {

        return token -> {
            Instant now = Instant.now();

            return switch (token) {

                case "customer-test-token" -> Mono.just(
                        createJwt(
                                token,
                                "money-transfer-web",
                                List.of("CUSTOMER"),
                                now));

                case "operations-test-token" -> Mono.just(
                        createJwt(
                                token,
                                "money-transfer-web",
                                List.of("OPERATIONS"),
                                now));

                case "foreign-client-test-token" -> Mono.just(
                        createJwt(
                                token,
                                "another-client",
                                List.of("CUSTOMER"),
                                now));

                default -> Mono.error(
                        new JwtException("Unknown test token"));
            };
        };
    }

    private Jwt createJwt(
            String token,
            String authorizedParty,
            List<String> roles,
            Instant now) {

        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .claim("iss", ISSUER)
                .claim("aud", List.of("api-gateway"))
                .claim("azp", authorizedParty)
                .claim("realm_access", Map.of(
                        "roles", roles))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
