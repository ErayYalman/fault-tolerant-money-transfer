package com.fintech.reconciliation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "resilience4j.circuitbreaker.instances.transferService.wait-duration-in-open-state=150ms"
})
class CircuitBreakerIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("circuit_breaker_test_db")
            .withUsername("circuit_breaker_test_user")
            .withPassword("circuit_breaker_test_password");

    private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();

    private static final AtomicBoolean UPSTREAM_FAILS = new AtomicBoolean(true);

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
        UPSTREAM_FAILS.set(true);

        circuitBreakerRegistry
                .circuitBreaker("transferService")
                .reset();
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @Test
    void shouldOpenTransferCircuitBreakerAfterFourFailures() {
        CircuitBreaker transferBreaker = circuitBreakerRegistry.circuitBreaker("transferService");

        assertThat(transferBreaker.getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        failFourRequests();

        assertThat(transferBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        assertThat(REQUEST_COUNT.get()).isEqualTo(4);

        assertThatThrownBy(() -> transferServiceClient.findTransfers(FROM, TO))
                .isInstanceOf(CallNotPermittedException.class);

        // Açık devre yeni HTTP isteğine izin vermemeli.
        assertThat(REQUEST_COUNT.get()).isEqualTo(4);

        // Başka bir bağımlılığın devresi etkilenmemeli.
        assertThat(circuitBreakerRegistry
                .circuitBreaker("paymentAService")
                .getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldCloseTransferCircuitBreakerAfterServiceRecovers()
            throws InterruptedException {

        CircuitBreaker transferBreaker = circuitBreakerRegistry.circuitBreaker("transferService");

        failFourRequests();

        assertThat(transferBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        // Uzak servis tekrar başarılı yanıt vermeye başlıyor.
        UPSTREAM_FAILS.set(false);

        // Test konfigürasyonundaki 150 ms'lik açık devre süresinin dolmasını bekle.
        Thread.sleep(300);

        // İlk başarılı çağrı devreyi HALF_OPEN durumuna taşımalı.
        assertThat(transferServiceClient.findTransfers(FROM, TO))
                .isEmpty();

        assertThat(transferBreaker.getState())
                .isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // İkinci başarılı deneme ile devre CLOSED durumuna dönmeli.
        assertThat(transferServiceClient.findTransfers(FROM, TO))
                .isEmpty();

        assertThat(transferBreaker.getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        // Dört başarısız ve iki başarılı HTTP çağrısı yapıldı.
        assertThat(REQUEST_COUNT.get()).isEqualTo(6);

        // Kapanan devre sonraki çağrılara da izin vermeli.
        assertThat(transferServiceClient.findTransfers(FROM, TO))
                .isEmpty();

        assertThat(REQUEST_COUNT.get()).isEqualTo(7);
    }

    private void failFourRequests() {
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> transferServiceClient.findTransfers(FROM, TO))
                    .isInstanceOf(
                            HttpServerErrorException.ServiceUnavailable.class);
        }
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);

            server.createContext(
                    "/internal/transfers",
                    CircuitBreakerIntegrationTest::handleRequest);

            server.start();
            return server;

        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void handleRequest(HttpExchange exchange) {
        REQUEST_COUNT.incrementAndGet();

        boolean shouldFail = UPSTREAM_FAILS.get();

        int status = shouldFail ? 503 : 200;

        String response = shouldFail
                ? "temporarily unavailable"
                : "[]";

        byte[] body = response.getBytes(StandardCharsets.UTF_8);

        try {
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json");

            exchange.sendResponseHeaders(status, body.length);

            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            }

        } catch (IOException ignored) {
            // İstemci bağlantıyı kapatmış olabilir.
        } finally {
            exchange.close();
        }
    }
}