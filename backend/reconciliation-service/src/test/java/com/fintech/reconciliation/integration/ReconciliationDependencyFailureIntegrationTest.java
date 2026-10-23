package com.fintech.reconciliation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReconciliationDependencyFailureIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reconciliation_dependency_failure_db")
            .withUsername("reconciliation_dependency_failure_user")
            .withPassword("reconciliation_dependency_failure_password");

    private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();

    private static final HttpServer TRANSFER_UPSTREAM = startUpstream();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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
                        + TRANSFER_UPSTREAM.getAddress().getPort());
    }

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM anomaly_scores");
        jdbcTemplate.update("DELETE FROM reconciliation_discrepancies");
        jdbcTemplate.update("DELETE FROM reconciliation_runs");

        REQUEST_COUNT.set(0);

        circuitBreakerRegistry
                .circuitBreaker("transferService")
                .reset();
    }

    @AfterAll
    static void stopUpstream() {
        TRANSFER_UPSTREAM.stop(0);
    }

    @Test
    void shouldPersistFailedRunsAndStopCallingUnavailableTransferService()
            throws Exception {

        CircuitBreaker transferBreaker = circuitBreakerRegistry.circuitBreaker("transferService");

        assertThat(transferBreaker.getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        for (int i = 0; i < 4; i++) {
            ResponseEntity<String> response = postManualRun();

            assertThat(response.getStatusCode())
                    .isEqualTo(HttpStatus.BAD_GATEWAY);

            JsonNode error = objectMapper.readTree(response.getBody());

            String runId = error.path("runId").asText();

            assertThat(runId).isNotBlank();

            String persistedStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM reconciliation_runs WHERE id = ?",
                    String.class,
                    java.util.UUID.fromString(runId));

            String errorMessage = jdbcTemplate.queryForObject(
                    "SELECT error_message FROM reconciliation_runs WHERE id = ?",
                    String.class,
                    java.util.UUID.fromString(runId));

            assertThat(persistedStatus).isEqualTo("FAILED");
            assertThat(errorMessage).isNotBlank();
        }

        assertThat(REQUEST_COUNT.get()).isEqualTo(4);

        assertThat(transferBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        ResponseEntity<String> rejectedResponse = postManualRun();

        assertThat(rejectedResponse.getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);

        JsonNode error = objectMapper.readTree(rejectedResponse.getBody());

        String runId = error.path("runId").asText();

        assertThat(runId).isNotBlank();

        assertThat(REQUEST_COUNT.get()).isEqualTo(4);

        String persistedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM reconciliation_runs WHERE id = ?",
                String.class,
                java.util.UUID.fromString(runId));

        assertThat(persistedStatus).isEqualTo("FAILED");

        assertThat(circuitBreakerRegistry
                .circuitBreaker("paymentAService")
                .getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private ResponseEntity<String> postManualRun() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = """
                {
                  "businessDate": "2026-10-20"
                }
                """;

        return restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/reconciliation/run",
                new HttpEntity<>(requestBody, headers),
                String.class);
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);

            server.createContext(
                    "/internal/transfers",
                    ReconciliationDependencyFailureIntegrationTest::handleRequest);

            server.start();
            return server;

        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void handleRequest(HttpExchange exchange) {
        REQUEST_COUNT.incrementAndGet();

        byte[] body = "temporarily unavailable"
                .getBytes(StandardCharsets.UTF_8);

        try {
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "text/plain");

            exchange.sendResponseHeaders(503, body.length);

            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            }
        } catch (IOException ignored) {
        } finally {
            exchange.close();
        }
    }
}