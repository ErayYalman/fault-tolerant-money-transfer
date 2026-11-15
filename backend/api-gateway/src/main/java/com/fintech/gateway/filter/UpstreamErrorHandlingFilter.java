package com.fintech.gateway.filter;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

@Component
public class UpstreamErrorHandlingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        return chain.filter(exchange)
                .onErrorMap(
                        error -> !exchange.getResponse().isCommitted()
                                && resolveStatus(error) != null,
                        error -> {
                            HttpStatus status = resolveStatus(error);

                            String reason = status == HttpStatus.GATEWAY_TIMEOUT
                                    ? "The upstream service did not respond "
                                            + "within the configured timeout."
                                    : "The upstream service could not be reached.";

                            return new ResponseStatusException(
                                    status,
                                    reason,
                                    error);
                        });
    }

    static HttpStatus resolveStatus(Throwable error) {

        if (hasCause(error, TimeoutException.class)
                || hasCause(error, ConnectTimeoutException.class)
                || hasCause(error, ReadTimeoutException.class)) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }

        if (hasCause(error, ConnectException.class)
                || hasCause(error, UnknownHostException.class)
                || hasCause(error, PrematureCloseException.class)) {
            return HttpStatus.BAD_GATEWAY;
        }

        return null;
    }

    private static boolean hasCause(
            Throwable error,
            Class<? extends Throwable> exceptionType) {

        Throwable current = error;

        while (current != null) {
            if (exceptionType.isInstance(current)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}