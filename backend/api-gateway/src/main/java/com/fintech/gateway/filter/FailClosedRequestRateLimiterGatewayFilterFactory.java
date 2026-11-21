package com.fintech.gateway.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

public final class FailClosedRequestRateLimiterGatewayFilterFactory
        extends RequestRateLimiterGatewayFilterFactory {

    private static final Logger log = LoggerFactory.getLogger(
            FailClosedRequestRateLimiterGatewayFilterFactory.class);

    private static final String EMPTY_KEY = "____EMPTY_KEY__";

    public FailClosedRequestRateLimiterGatewayFilterFactory(
            RedisRateLimiter defaultRateLimiter,
            KeyResolver defaultKeyResolver) {
        super(defaultRateLimiter, defaultKeyResolver);
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewayFilter apply(Config config) {
        KeyResolver keyResolver = config.getKeyResolver() != null
                ? config.getKeyResolver()
                : getDefaultKeyResolver();

        RateLimiter<Object> rateLimiter = (RateLimiter<Object>) (config.getRateLimiter() != null
                ? config.getRateLimiter()
                : getDefaultRateLimiter());

        boolean denyEmptyKey = config.getDenyEmptyKey() != null
                ? config.getDenyEmptyKey()
                : isDenyEmptyKey();

        String emptyKeyStatus = config.getEmptyKeyStatus() != null
                ? config.getEmptyKeyStatus()
                : getEmptyKeyStatusCode();

        return (exchange, chain) -> keyResolver.resolve(exchange)
                .defaultIfEmpty(EMPTY_KEY)
                .flatMap(key -> {
                    if (EMPTY_KEY.equals(key)) {
                        if (denyEmptyKey) {
                            setResponseStatus(
                                    exchange,
                                    HttpStatusHolder.parse(emptyKeyStatus));
                            return exchange.getResponse().setComplete();
                        }

                        return chain.filter(exchange);
                    }

                    String routeId = config.getRouteId();

                    if (routeId == null) {
                        Route route = exchange.getAttribute(
                                ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                        routeId = java.util.Objects.requireNonNull(
                                route, "Route not found").getId();
                    }

                    final String resolvedRouteId = routeId;

                    Mono<Optional<RateLimiter.Response>> decision = Mono
                            .defer(() -> rateLimiter.isAllowed(resolvedRouteId, key))
                            .map(Optional::of)
                            .onErrorResume(exception -> {
                                log.error(
                                        "Rate limiter evaluation failed; rejecting request",
                                        exception);
                                return Mono.just(Optional.empty());
                            })
                            .defaultIfEmpty(Optional.empty());

                    return decision.flatMap(result -> {
                        if (result.isEmpty()) {
                            return rejectBecauseRedisUnavailable(exchange);
                        }

                        RateLimiter.Response response = result.get();

                        String remainingTokens = response.getHeaders().get(
                                RedisRateLimiter.REMAINING_HEADER);

                        /*
                         * RedisRateLimiter v5.0.3 returns -1 when its Redis
                         * operation fails. Missing this header is also treated
                         * as unavailable so that the route cannot fail open.
                         */
                        if (remainingTokens == null || "-1".equals(remainingTokens)) {
                            log.error(
                                    "Redis rate limiter did not return a valid token count; "
                                            + "rejecting request");
                            return rejectBecauseRedisUnavailable(exchange);
                        }

                        response.getHeaders().forEach(
                                (name, value) -> exchange.getResponse()
                                        .getHeaders().add(name, value));

                        if (response.isAllowed()) {
                            return chain.filter(exchange);
                        }

                        exchange.getResponse().setStatusCode(config.getStatusCode());
                        return exchange.getResponse().setComplete();
                    });
                });
    }

    private Mono<Void> rejectBecauseRedisUnavailable(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return exchange.getResponse().setComplete();
    }
}