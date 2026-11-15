package com.fintech.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayRoutingIntegrationTest {

    private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();

    private static final AtomicReference<String> LAST_METHOD = new AtomicReference<>();

    private static final AtomicReference<String> LAST_PATH = new AtomicReference<>();

    private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();

    private static final HttpServer UPSTREAM = startUpstream();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @DynamicPropertySource
    static void configureGatewayRoute(
            DynamicPropertyRegistry registry) {

        registry.add(
                "GATEWAY_TRANSFER_SERVICE_URL",
                () -> "http://127.0.0.1:"
                        + UPSTREAM.getAddress().getPort());
    }

    @BeforeEach
    void resetCapturedRequest() {
        REQUEST_COUNT.set(0);
        LAST_METHOD.set(null);
        LAST_PATH.set(null);
        LAST_BODY.set(null);
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @Test
    void shouldForwardCreateTransferRequestWithoutChangingPathOrBody()
            throws Exception {

        String requestBody = """
                {
                  "amount": 1500.00,
                  "currency": "TRY",
                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                  "toAccountId": "22222222-2222-2222-2222-222222222222",
                  "idempotencyKey": "gateway-integration-test"
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        gatewayBaseUrl()
                                + "/api/v1/transfers?source=test"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("DEBIT_REQUESTED");

        assertThat(REQUEST_COUNT.get()).isEqualTo(1);
        assertThat(LAST_METHOD.get()).isEqualTo("POST");
        assertThat(LAST_PATH.get())
                .isEqualTo("/api/v1/transfers?source=test");
        assertThat(LAST_BODY.get()).isEqualTo(requestBody);
    }

    @Test
    void shouldNotExposeInternalOrReconciliationRoutes()
            throws Exception {

        HttpResponse<String> internalResponse = sendGet(
                "/internal/transfers");

        HttpResponse<String> reconciliationResponse = sendGet(
                "/api/v1/reconciliation/run");

        assertThat(internalResponse.statusCode()).isEqualTo(404);
        assertThat(reconciliationResponse.statusCode()).isEqualTo(404);

        // Gateway bu istekleri Transfer Service'e iletmemeli.
        assertThat(REQUEST_COUNT.get()).isZero();
    }

    private HttpResponse<String> sendGet(String path)
            throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gatewayBaseUrl() + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());
    }

    private String gatewayBaseUrl() {
        return "http://localhost:" + gatewayPort;
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);

            server.createContext(
                    "/",
                    ApiGatewayRoutingIntegrationTest::handleRequest);

            server.start();
            return server;

        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void handleRequest(HttpExchange exchange) {
        REQUEST_COUNT.incrementAndGet();
        LAST_METHOD.set(exchange.getRequestMethod());
        LAST_PATH.set(exchange.getRequestURI().toString());

        try {
            LAST_BODY.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));

            byte[] responseBody = """
                    {
                      "transferId": "11111111-1111-1111-1111-111111111111",
                      "status": "DEBIT_REQUESTED"
                    }
                    """.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json");

            exchange.sendResponseHeaders(201, responseBody.length);

            try (OutputStream output = exchange.getResponseBody()) {
                output.write(responseBody);
            }

        } catch (IOException ignored) {
            // İstemci bağlantıyı kapatmış olabilir.
        } finally {
            exchange.close();
        }
    }
}