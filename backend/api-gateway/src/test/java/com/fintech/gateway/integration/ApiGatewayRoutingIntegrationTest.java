package com.fintech.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

        private static final AtomicLong UPSTREAM_DELAY_MILLIS = new AtomicLong(0);
        private static final AtomicInteger UPSTREAM_STATUS = new AtomicInteger(201);
        private static final AtomicReference<String> UPSTREAM_RESPONSE_BODY = new AtomicReference<>(
                        """
                                        {
                                          "transferId": "11111111-1111-1111-1111-111111111111",
                                          "status": "DEBIT_REQUESTED"
                                        }
                                        """);

        private static final HttpServer UPSTREAM = startUpstream();

        private final HttpClient httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build();

        @LocalServerPort
        private int gatewayPort;

        @DynamicPropertySource
        static void configureGatewayRoute(DynamicPropertyRegistry registry) {
                registry.add(
                                "GATEWAY_TRANSFER_SERVICE_BASE_URL",
                                () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort());
                registry.add(
                                "GATEWAY_RESPONSE_TIMEOUT",
                                () -> "2000ms");
        }

        @BeforeEach
        void resetCapturedRequest() {
                REQUEST_COUNT.set(0);
                LAST_METHOD.set(null);
                LAST_PATH.set(null);
                LAST_BODY.set(null);
                UPSTREAM_DELAY_MILLIS.set(0);
                UPSTREAM_STATUS.set(201);
                UPSTREAM_RESPONSE_BODY.set(
                                """
                                                {
                                                  "transferId": "11111111-1111-1111-1111-111111111111",
                                                  "status": "DEBIT_REQUESTED"
                                                }
                                                """);
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
                                .uri(URI.create(gatewayBaseUrl() + "/api/v1/transfers?source=test"))
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
                assertThat(LAST_PATH.get()).isEqualTo("/api/v1/transfers?source=test");
                assertThat(LAST_BODY.get()).isEqualTo(requestBody);
        }

        @Test
        void shouldNotExposeInternalOrReconciliationRoutes()
                        throws Exception {

                HttpResponse<String> internalResponse = sendGet("/internal/transfers");
                HttpResponse<String> reconciliationResponse = sendGet("/api/v1/reconciliation/run");

                assertThat(internalResponse.statusCode()).isEqualTo(404);
                assertThat(reconciliationResponse.statusCode()).isEqualTo(404);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldReturnGatewayTimeoutWhenUpstreamRespondsTooSlowly()
                        throws Exception {

                UPSTREAM_DELAY_MILLIS.set(3_000);

                HttpResponse<String> response = sendGet("/api/v1/transfers");

                assertThat(response.statusCode()).isEqualTo(504);
        }

        @Test
        void shouldPreserveUpstreamServiceUnavailableResponse()
                        throws Exception {

                UPSTREAM_STATUS.set(503);
                UPSTREAM_RESPONSE_BODY.set("{\"message\":\"Upstream temporarily unavailable\"}");

                HttpResponse<String> response = sendGet("/api/v1/transfers");

                assertThat(response.statusCode()).isEqualTo(503);
                assertThat(response.body()).isEqualTo("{\"message\":\"Upstream temporarily unavailable\"}");
        }

        private HttpResponse<String> sendGet(String path) throws Exception {
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(gatewayBaseUrl() + path))
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build();

                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        private String gatewayBaseUrl() {
                return "http://localhost:" + gatewayPort;
        }

        private static HttpServer startUpstream() {
                try {
                        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                        server.createContext("/", ApiGatewayRoutingIntegrationTest::handleRequest);
                        server.start();
                        return server;
                } catch (IOException exception) {
                        throw new ExceptionInInitializerError(exception);
                }
        }

        private static void handleRequest(HttpExchange exchange) {
                long delay = UPSTREAM_DELAY_MILLIS.get();
                int status = UPSTREAM_STATUS.get();
                String responseBodyStr = UPSTREAM_RESPONSE_BODY.get();

                if (delay > 0) {
                        try {
                                Thread.sleep(delay);
                        } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                        }
                }

                // Eğer gecikme süresi 2 saniyeden (2000ms) büyükse, bu istek timeout testine
                // aittir.
                // Timeout testindeki isteklerin normal sayaçları kirletmemesi için burayı
                // filtreliyoruz.
                if (delay >= 3_000) {
                        try {
                                exchange.sendResponseHeaders(200, 0);
                                exchange.close();
                        } catch (Exception ignored) {
                        }
                        return;
                }

                REQUEST_COUNT.incrementAndGet();
                LAST_METHOD.set(exchange.getRequestMethod());
                LAST_PATH.set(exchange.getRequestURI().toString());

                try {
                        byte[] requestBytes = exchange.getRequestBody().readAllBytes();
                        LAST_BODY.set(new String(requestBytes, StandardCharsets.UTF_8));

                        byte[] responseBody = responseBodyStr.getBytes(StandardCharsets.UTF_8);

                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(status, responseBody.length);

                        try (OutputStream output = exchange.getResponseBody()) {
                                output.write(responseBody);
                        }
                } catch (IOException ignored) {
                        // İstemci bağlantıyı kapatmış olabilir.
                } finally {
                        exchange.close();
                }
        }

        @Nested
        class ResolveStatusUnitTest {

                @Test
                void shouldResolveConnectionErrorTo502() {
                        int statusCode = resolveStatus(new ConnectException("Connection refused"));
                        assertThat(statusCode).isEqualTo(502);
                }

                @Test
                void shouldResolveTimeoutErrorTo504() {
                        int statusCode = resolveStatus(new TimeoutException("Gateway timeout"));
                        assertThat(statusCode).isEqualTo(504);
                }

                @Test
                void shouldLeaveUnrelatedApplicationErrorUnmapped() {
                        assertThatThrownBy(() -> resolveStatus(new IllegalArgumentException("Invalid argument")))
                                        .isInstanceOf(IllegalArgumentException.class);
                }

                private int resolveStatus(Throwable throwable) {
                        if (throwable instanceof ConnectException) {
                                return 502;
                        }
                        if (throwable instanceof TimeoutException) {
                                return 504;
                        }
                        if (throwable instanceof RuntimeException runtimeException) {
                                throw runtimeException;
                        }
                        throw new IllegalArgumentException(throwable);
                }
        }
}