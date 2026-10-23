package com.fintech.reconciliation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpServerErrorException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fintech.reconciliation.infrastructure.client.TransferServiceClient;
import com.sun.net.httpserver.HttpServer;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CircuitBreakerIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("circuit_breaker_test_db")
            .withUsername("circuit_breaker_test_user")
            .withPassword("circuit_breaker_test_password");

    private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();

    private static final HttpServer UPSTREAM = startUpstream();

    private static final Instant FROM = Instant.parse("2026-10-20T00:00:00Z");

    private static final Instant TO = Instant.parse("2026-10-21T00:00:00Z");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add(
                "reconciliation.clients.transfer-base-url",
                () -> "http://127.0.0.1:"
                        + UPSTREAM.getAddress().getPort());
    }

    @Autowired
    private TransferServiceClient transferServiceClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetRequestCount() {
        REQUEST_COUNT.set(0);
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @Test
    void shouldOpenTransferCircuitBreakerAfterFourFailures()
            throws Exception {

        CircuitBreaker transferBreaker = circuitBreakerRegistry.circuitBreaker("transferService");

        assertThat(transferBreaker.getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> transferServiceClient.findTransfers(FROM, TO))
                    .isInstanceOf(
                            HttpServerErrorException.ServiceUnavailable.class);
        }

        assertThat(transferBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        assertThat(REQUEST_COUNT.get()).isEqualTo(4);

        assertThatThrownBy(() -> transferServiceClient.findTransfers(FROM, TO))
                .isInstanceOf(CallNotPermittedException.class);

        assertThat(REQUEST_COUNT.get()).isEqualTo(4);

        assertThat(circuitBreakerRegistry
                .circuitBreaker("paymentAService")
                .getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);

            server.createContext("/internal/transfers", exchange -> {
                REQUEST_COUNT.incrementAndGet();

                byte[] body = "temporarily unavailable"
                        .getBytes(StandardCharsets.UTF_8);

                exchange.sendResponseHeaders(503, body.length);

                try (OutputStream responseBody = exchange.getResponseBody()) {
                    responseBody.write(body);
                } catch (IOException ignored) {
                    
                }
            });

            server.start();
            return server;

        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}