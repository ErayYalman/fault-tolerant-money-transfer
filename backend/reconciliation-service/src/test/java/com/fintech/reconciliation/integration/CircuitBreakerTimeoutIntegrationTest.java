package com.fintech.reconciliation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fintech.reconciliation.infrastructure.client.TransferServiceClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.http.client.read-timeout=150ms",
        "resilience4j.circuitbreaker.instances.transferService.wait-duration-in-open-state=20s"
})
class CircuitBreakerTimeoutIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("circuit_breaker_timeout_test_db")
            .withUsername("circuit_breaker_timeout_test_user")
            .withPassword("circuit_breaker_timeout_test_password");

    private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();

    private static final ExecutorService UPSTREAM_EXECUTOR = Executors.newCachedThreadPool();

    private static final HttpServer UPSTREAM = startUpstream();

    private static final Instant FROM = Instant.parse("2026-10-20T00:00:00Z");

    private static final Instant TO = Instant.parse("2026-10-21T00:00:00Z");

    @Autowired
    private TransferServiceClient transferServiceClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

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

    @BeforeEach
    void resetState() {
        REQUEST_COUNT.set(0);

        circuitBreakerRegistry
                .circuitBreaker("transferService")
                .reset();
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
        UPSTREAM_EXECUTOR.shutdownNow();
    }

    @Test
    void shouldOpenCircuitBreakerAfterFourReadTimeoutFailures() {
        CircuitBreaker transferBreaker = circuitBreakerRegistry.circuitBreaker("transferService");

        assertThat(transferBreaker.getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> transferServiceClient.findTransfers(FROM, TO))
                    .isInstanceOf(ResourceAccessException.class);
        }

        assertThat(transferBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        assertThat(REQUEST_COUNT.get()).isEqualTo(4);

        assertThatThrownBy(() -> transferServiceClient.findTransfers(FROM, TO))
                .isInstanceOf(CallNotPermittedException.class);

        assertThat(REQUEST_COUNT.get()).isEqualTo(4);
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);

            server.setExecutor(UPSTREAM_EXECUTOR);

            server.createContext(
                    "/internal/transfers",
                    CircuitBreakerTimeoutIntegrationTest::handleRequest);

            server.start();
            return server;

        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void handleRequest(HttpExchange exchange) {
        REQUEST_COUNT.incrementAndGet();

        try {
            Thread.sleep(1000);

            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json");

            exchange.sendResponseHeaders(200, body.length);

            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {

        } finally {
            exchange.close();
        }
    }
}