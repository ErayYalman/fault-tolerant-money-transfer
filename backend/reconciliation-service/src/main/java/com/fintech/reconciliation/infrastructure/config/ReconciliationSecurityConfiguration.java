package com.fintech.reconciliation.infrastructure.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ReconciliationSecurityConfiguration {

        private static final String WEB_CLIENT_ID = "money-transfer-web";

        private static final Set<String> ALLOWED_ROLES = Set.of(
                        "OPERATIONS",
                        "ADMIN");

        @Bean
        public JwtAuthenticationConverter reconciliationJwtAuthenticationConverter() {

                Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter = jwt -> {
                        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

                        // Only tokens issued to our browser application
                        // may use human-user realm roles.
                        if (!WEB_CLIENT_ID.equals(jwt.getClaimAsString("azp"))) {
                                return authorities;
                        }

                        Object realmAccessClaim = jwt.getClaims().get("realm_access");

                        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
                                return authorities;
                        }

                        Object rolesClaim = realmAccess.get("roles");

                        if (!(rolesClaim instanceof Collection<?> roles)) {
                                return authorities;
                        }

                        for (Object role : roles) {
                                if (role instanceof String roleName
                                                && ALLOWED_ROLES.contains(roleName)) {

                                        authorities.add(
                                                        new SimpleGrantedAuthority("ROLE_" + roleName));
                                }
                        }

                        return authorities;
                };

                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
                converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

                return converter;
        }

        @Bean
        public SecurityFilterChain reconciliationSecurityFilterChain(
                        HttpSecurity http,
                        JwtAuthenticationConverter reconciliationJwtAuthenticationConverter)
                        throws Exception {

                return http
                                .csrf(AbstractHttpConfigurer::disable)

                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                .authorizeHttpRequests(authorize -> authorize

                                                // Health probes remain publicly accessible.
                                                .requestMatchers(
                                                                "/actuator/health",
                                                                "/actuator/health/**")
                                                .permitAll()

                                                // OpenAPI / Swagger UI documentation.
                                                .requestMatchers(
                                                                "/v3/api-docs",
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html")
                                                .permitAll()

                                                // Manual reconciliation and operational monitoring.
                                                .requestMatchers(
                                                                "/api/v1/reconciliation/**",
                                                                "/actuator/**")
                                                .hasAnyRole("OPERATIONS", "ADMIN")

                                                // Everything else is denied by default.
                                                .anyRequest()
                                                .denyAll())

                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt
                                                                .jwtAuthenticationConverter(
                                                                                reconciliationJwtAuthenticationConverter)))

                                .build();
        }
}