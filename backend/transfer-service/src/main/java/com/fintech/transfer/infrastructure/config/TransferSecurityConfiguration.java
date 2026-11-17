
package com.fintech.transfer.infrastructure.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class TransferSecurityConfiguration {

    private static final String SERVICE_CLIENT_ID = "transfer-service";
    private static final String RECONCILIATION_CLIENT_ID = "reconciliation-service";
    private static final String WEB_CLIENT_ID = "money-transfer-web";

    @Bean
    public JwtAuthenticationConverter transferJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

        Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter = jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();

            Collection<GrantedAuthority> scopeAuthorities = scopeConverter.convert(jwt);

            if (scopeAuthorities != null) {
                authorities.addAll(scopeAuthorities);
            }

            // Human-user realm roles are trusted only from the browser client.
            if (WEB_CLIENT_ID.equals(jwt.getClaimAsString("azp"))) {
                addRoles(
                        jwt.getClaim("realm_access"),
                        authorities);
            }

            // Service client roles are accepted only from the
            // dedicated reconciliation-service client.
            if (RECONCILIATION_CLIENT_ID.equals(
                    jwt.getClaimAsString("azp"))) {

                Object resourceAccessClaim = jwt.getClaims().get("resource_access");

                if (resourceAccessClaim instanceof Map<?, ?> resourceAccess) {
                    Object transferAccess = resourceAccess.get(SERVICE_CLIENT_ID);

                    addRoles(transferAccess, authorities);
                }
            }

            return authorities;
        };

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return converter;
    }

    private void addRoles(
            Object accessClaim,
            Set<GrantedAuthority> authorities) {

        if (!(accessClaim instanceof Map<?, ?> access)) {
            return;
        }

        Object rolesClaim = access.get("roles");

        if (!(rolesClaim instanceof Collection<?> roles)) {
            return;
        }

        for (Object role : roles) {
            if (role instanceof String roleName && !roleName.isBlank()) {
                authorities.add(
                        new SimpleGrantedAuthority("ROLE_" + roleName));
            }
        }
    }

    @Bean
    public SecurityFilterChain transferSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter transferJwtAuthenticationConverter)
            throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**")
                        .permitAll()

                        .requestMatchers(
                                "/internal/transfers",
                                "/internal/transfers/**")
                        .hasAuthority("ROLE_reconciliation-read")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/transfers")
                        .hasRole("CUSTOMER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/transfers",
                                "/api/v1/transfers/**")
                        .hasRole("CUSTOMER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/recipients",
                                "/api/v1/recipients/**")
                        .hasRole("CUSTOMER")

                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .authenticated()

                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        transferJwtAuthenticationConverter)))
                .build();
    }
}
