package com.fintech.gateway.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class GatewaySecurityConfiguration {

        private static final String WEB_CLIENT_ID = "money-transfer-web";

        @Bean
        public CorsConfigurationSource gatewayCorsConfigurationSource(
                        @Value("${app.cors.allowed-origin:http://localhost:5173}") String allowedOrigin) {

                CorsConfiguration configuration = new CorsConfiguration();

                configuration.setAllowedOrigins(List.of(allowedOrigin));

                configuration.setAllowedMethods(List.of(
                                "GET",
                                "POST",
                                "OPTIONS"));

                configuration.setAllowedHeaders(List.of(
                                "Authorization",
                                "Content-Type"));

                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

                source.registerCorsConfiguration("/**", configuration);

                return source;
        }

        @Bean
        public Converter<Jwt, Mono<AbstractAuthenticationToken>> gatewayJwtAuthenticationConverter() {

                return jwt -> {
                        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

                        // Only tokens issued to our browser application may use
                        // the public customer routes.
                        if (WEB_CLIENT_ID.equals(jwt.getClaimAsString("azp"))) {

                                Object realmAccessClaim = jwt.getClaims().get("realm_access");

                                if (realmAccessClaim instanceof Map<?, ?> realmAccess) {
                                        Object rolesClaim = realmAccess.get("roles");

                                        if (rolesClaim instanceof Collection<?> roles) {
                                                for (Object role : roles) {
                                                        if (role instanceof String roleName
                                                                        && !roleName.isBlank()) {

                                                                authorities.add(
                                                                                new SimpleGrantedAuthority(
                                                                                                "ROLE_" + roleName));
                                                        }
                                                }
                                        }
                                }
                        }

                        return Mono.just(
                                        new JwtAuthenticationToken(jwt, authorities));
                };
        }

        @Bean
        public SecurityWebFilterChain gatewaySecurityFilterChain(
                        ServerHttpSecurity http,
                        Converter<Jwt, Mono<AbstractAuthenticationToken>> gatewayJwtAuthenticationConverter,
                        CorsConfigurationSource corsConfigurationSource) {

                return http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                                .authorizeExchange(exchanges -> exchanges

                                                .pathMatchers(
                                                                "/actuator/health",
                                                                "/actuator/health/**")
                                                .permitAll()

                                                .pathMatchers(
                                                                HttpMethod.POST,
                                                                "/api/v1/transfers")
                                                .hasRole("CUSTOMER")

                                                .pathMatchers(
                                                                HttpMethod.GET,
                                                                "/api/v1/transfers",
                                                                "/api/v1/transfers/**")
                                                .hasRole("CUSTOMER")

                                                .pathMatchers(
                                                                HttpMethod.GET,
                                                                "/api/v1/accounts",
                                                                "/api/v1/accounts/**")
                                                .hasRole("CUSTOMER")

                                                .pathMatchers(
                                                                HttpMethod.POST,
                                                                "/api/v1/accounts/internal-transfers")
                                                .hasRole("CUSTOMER")

                                                .pathMatchers(
                                                                HttpMethod.GET,
                                                                "/api/v1/recipients",
                                                                "/api/v1/recipients/**")
                                                .hasRole("CUSTOMER")

                                                .pathMatchers(
                                                                HttpMethod.POST,
                                                                "/api/v1/reconciliation",
                                                                "/api/v1/reconciliation/**")
                                                .hasAnyRole("OPERATIONS", "ADMIN")

                                                .pathMatchers(
                                                                HttpMethod.GET,
                                                                "/api/v1/reconciliation",
                                                                "/api/v1/reconciliation/**")
                                                .hasAnyRole("OPERATIONS", "ADMIN")

                                                // No other route is publicly accessible.
                                                .anyExchange().denyAll())

                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                                                gatewayJwtAuthenticationConverter)))

                                .build();
        }
}
