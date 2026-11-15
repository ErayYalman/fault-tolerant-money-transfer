package com.fintech.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class UpstreamErrorHandlingFilterTest {

    @Test
    void shouldMapConnectionRefusedToBadGateway() {

        HttpStatus status = UpstreamErrorHandlingFilter.resolveStatus(
                new ConnectException("Connection refused"));

        assertThat(status).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void shouldMapUnknownHostToBadGateway() {

        HttpStatus status = UpstreamErrorHandlingFilter.resolveStatus(
                new UnknownHostException("Unknown host"));

        assertThat(status).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void shouldMapTimeoutToGatewayTimeout() {

        HttpStatus status = UpstreamErrorHandlingFilter.resolveStatus(
                new TimeoutException("Request timed out"));

        assertThat(status).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void shouldResolveNestedTimeoutCause() {

        RuntimeException exception = new RuntimeException(
                "Upstream request failed",
                new TimeoutException("Read timeout"));

        HttpStatus status = UpstreamErrorHandlingFilter.resolveStatus(
                exception);

        assertThat(status).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void shouldLeaveUnrelatedApplicationErrorsUnmapped() {

        HttpStatus status = UpstreamErrorHandlingFilter.resolveStatus(
                new IllegalArgumentException("Invalid argument"));

        assertThat(status).isNull();
    }
}