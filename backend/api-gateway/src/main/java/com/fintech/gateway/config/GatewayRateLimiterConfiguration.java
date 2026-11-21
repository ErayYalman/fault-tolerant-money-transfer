package com.fintech.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fintech.gateway.filter.FailClosedRequestRateLimiterGatewayFilterFactory;

import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class GatewayRateLimiterConfiguration {

    @Bean
    public KeyResolver jwtSubjectKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .flatMap(authentication -> Mono.justOrEmpty(
                        authentication.getToken().getSubject()))
                .filter(subject -> !subject.isBlank());
    }

    @Bean
    FailClosedRequestRateLimiterGatewayFilterFactory failClosedRequestRateLimiterGatewayFilterFactory(
            RedisRateLimiter redisRateLimiter,
            KeyResolver jwtSubjectKeyResolver) {
        return new FailClosedRequestRateLimiterGatewayFilterFactory(
                redisRateLimiter,
                jwtSubjectKeyResolver);
    }
}