
package com.fintech.payment.a.infrastructure.config;

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
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class PaymentASecurityConfiguration {

    private static final String RESOURCE_CLIENT_ID = "payment-service-a";
    private static final String RECONCILIATION_CLIENT_ID = "reconciliation-service";
    private static final String WEB_CLIENT_ID = "money-transfer-web";

    @Bean
    public JwtAuthenticationConverter paymentAJwtAuthenticationConverter() {

        Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter = jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();

            String authorizedParty = jwt.getClaimAsString("azp");

            if (WEB_CLIENT_ID.equals(authorizedParty)) {
                Object realmAccessClaim = jwt.getClaims().get("realm_access");

                if (realmAccessClaim instanceof Map<?, ?> realmAccess) {
                    Object rolesClaim = realmAccess.get("roles");

                    if (rolesClaim instanceof Collection<?> roles) {
                        for (Object role : roles) {
                            if ("CUSTOMER".equals(role)) {
                                authorities.add(
                                        new SimpleGrantedAuthority("ROLE_CUSTOMER"));
                            }
                        }
                    }
                }

                return authorities;
            }

            if (!RECONCILIATION_CLIENT_ID.equals(authorizedParty)) {
                return authorities;
            }

            Object resourceAccessClaim = jwt.getClaims().get("resource_access");

            if (!(resourceAccessClaim instanceof Map<?, ?> resourceAccess)) {
                return authorities;
            }

            Object paymentAAccess = resourceAccess.get(RESOURCE_CLIENT_ID);

            if (!(paymentAAccess instanceof Map<?, ?> access)) {
                return authorities;
            }

            Object rolesClaim = access.get("roles");

            if (!(rolesClaim instanceof Collection<?> roles)) {
                return authorities;
            }

            for (Object role : roles) {
                if (role instanceof String roleName && !roleName.isBlank()) {
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
    public SecurityFilterChain paymentASecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter paymentAJwtAuthenticationConverter)
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
                                HttpMethod.GET,
                                "/api/v1/accounts",
                                "/api/v1/accounts/**")
                        .hasAuthority("ROLE_CUSTOMER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/internal/ledger",
                                "/internal/ledger/**")
                        .hasAuthority("ROLE_reconciliation-read")

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
                                        paymentAJwtAuthenticationConverter)))
                .build();
    }
}
