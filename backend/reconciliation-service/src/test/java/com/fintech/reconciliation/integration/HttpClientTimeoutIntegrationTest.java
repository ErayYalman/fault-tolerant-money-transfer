package com.fintech.reconciliation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fintech.reconciliation.domain.model.AnomalyFeatures;
import com.fintech.reconciliation.domain.port.out.AnomalyScoringProvider;
import com.fintech.reconciliation.infrastructure.client.PaymentServiceAClient;
import com.fintech.reconciliation.infrastructure.client.PaymentServiceBClient;
import com.fintech.reconciliation.infrastructure.client.TransferServiceClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HttpClientTimeoutIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("http_timeout_test_db")
            .withUsername("http_timeout_test_user")
            .withPassword("http_timeout_test_password");

    private static final HttpServer UPSTREAM = startUpstream();

    private static final Instant FROM = Instant.parse("2026-10-20T00:00:00Z");

    private static final Instant TO = Instant.parse("2026-10-21T00:00:00Z");

    private static final AnomalyFeatures FEATURES = new AnomalyFeatures(
            new BigDecimal("100.00"),
            500,
            80,
            390,
            1,
            1);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        String baseUrl = "http://127.0.0.1:"
                + UPSTREAM.getAddress().getPort();

        registry.add(
                "reconciliation.clients.transfer-base-url",
                () -> baseUrl);
        registry.add(
                "reconciliation.clients.payment-a-base-url",
                () -> baseUrl);
        registry.add(
                "reconciliation.clients.payment-b-base-url",
                () -> baseUrl);
        registry.add(
                "reconciliation.ml.base-url",
                () -> baseUrl);

        // Testte 5 saniyelik varsayılanı beklemeden timeout doğrulayabilmek için.
        registry.add("spring.http.client.read-timeout", () -> "150ms");
        registry.add("spring.http.client.connect-timeout", () -> "1s");
    }

    @Autowired
    private TransferServiceClient transferServiceClient;

    @Autowired
    private PaymentServiceAClient paymentServiceAClient;

    @Autowired
    private PaymentServiceBClient paymentServiceBClient;

    @Autowired
    private AnomalyScoringProvider anomalyScoringProvider;

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @Test
    void shouldApplyReadTimeoutToAllFourHttpClients() {
        assertTimesOut(() -> transferServiceClient.findTransfers(FROM, TO));

        assertTimesOut(() -> paymentServiceAClient.findLedgerEntries(FROM, TO));

        assertTimesOut(() -> paymentServiceBClient.findLedgerEntries(FROM, TO));

        assertTimesOut(() -> anomalyScoringProvider.score(FEATURES));
    }

    private void assertTimesOut(Runnable request) {
        long startedAt = System.nanoTime();

        assertThatThrownBy(request::run)
                .isInstanceOf(ResourceAccessException.class);

        long elapsedMillis = Duration.ofNanos(
                System.nanoTime() - startedAt).toMillis();

        assertThat(elapsedMillis)
                .as("HTTP request should respect the configured read timeout")
                .isLessThan(1000);
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);

            server.createContext("/", HttpClientTimeoutIntegrationTest::respondSlowly);
            server.start();

            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respondSlowly(HttpExchange exchange) {
        try {
            Thread.sleep(1500);

            String path = exchange.getRequestURI().getPath();

            String responseBody = "/score".equals(path)
                    ? """
                            {
                              "anomalyScore": 0.12,
                              "modelVersion": "timeout-test",
                              "isAnomaly": false
                            }
                            """
                    : "[]";

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json");

            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // İstemci timeout nedeniyle bağlantıyı kapatmış olabilir.
        } finally {
            exchange.close();
        }
    }
}