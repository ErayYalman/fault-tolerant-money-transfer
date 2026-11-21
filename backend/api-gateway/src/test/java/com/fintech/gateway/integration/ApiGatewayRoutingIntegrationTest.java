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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fintech.gateway.support.GatewayTestSecurityConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayTestSecurityConfiguration.class)
class ApiGatewayRoutingIntegrationTest {

        private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();
        private static final AtomicReference<String> LAST_METHOD = new AtomicReference<>();
        private static final AtomicReference<String> LAST_PATH = new AtomicReference<>();
        private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();
        private static final AtomicReference<String> LAST_AUTHORIZATION = new AtomicReference<>();

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

        private static final String REDIS_TEST_PASSWORD = "gateway-test-redis-password";

        @SuppressWarnings("resource")
        @Container
        static final GenericContainer<?> REDIS = new GenericContainer<>(
                        DockerImageName.parse("redis:8.10.3-alpine"))
                        .withExposedPorts(6379)
                        .withCommand(
                                        "redis-server",
                                        "--requirepass",
                                        REDIS_TEST_PASSWORD);

        @LocalServerPort
        private int gatewayPort;

        @DynamicPropertySource
        static void configureGatewayRoute(DynamicPropertyRegistry registry) {
                registry.add(
                                "GATEWAY_TRANSFER_SERVICE_BASE_URL",
                                () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort());
                registry.add(
                                "GATEWAY_RECONCILIATION_SERVICE_BASE_URL",
                                () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort());
                registry.add(
                                "GATEWAY_PAYMENT_A_SERVICE_BASE_URL",
                                () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort());
                registry.add(
                                "GATEWAY_RESPONSE_TIMEOUT",
                                () -> "2000ms");

                registry.add("REDIS_HOST", REDIS::getHost);
                registry.add(
                                "REDIS_SERVER_PORT",
                                () -> REDIS.getMappedPort(6379));
                registry.add(
                                "REDIS_PASSWORD",
                                () -> REDIS_TEST_PASSWORD);

                // Integration tests use a deliberately small bucket.
                registry.add(
                                "GATEWAY_TRANSFER_RATE_LIMIT_REPLENISH_RATE",
                                () -> "1");
                registry.add(
                                "GATEWAY_TRANSFER_RATE_LIMIT_BURST_CAPACITY",
                                () -> "2");
        }

        @BeforeEach
        void resetCapturedRequest() {
                REQUEST_COUNT.set(0);
                LAST_METHOD.set(null);
                LAST_PATH.set(null);
                LAST_BODY.set(null);
                LAST_AUTHORIZATION.set(null);
                UPSTREAM_DELAY_MILLIS.set(0);
                UPSTREAM_STATUS.set(201);
                UPSTREAM_RESPONSE_BODY.set(
                                """
                                                {
                                                  "transferId": "11111111-1111-1111-1111-111111111111",
                                                  "status": "DEBIT_REQUESTED"
                                                }
                                                """);

                try {
                        var result = REDIS.execInContainer(
                                        "redis-cli",
                                        "-a",
                                        REDIS_TEST_PASSWORD,
                                        "FLUSHDB");

                        assertThat(result.getExitCode()).isZero();

                } catch (Exception exception) {
                        throw new IllegalStateException(
                                        "Could not reset Redis test database",
                                        exception);
                }
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
                                  "toAccountNumber": "BANK-B-RECIPIENT-001",
                                  "idempotencyKey": "gateway-integration-test"
                                }
                                """;

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(gatewayBaseUrl() + "/api/v1/transfers?source=test"))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer customer-test-token")
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
                assertThat(LAST_AUTHORIZATION.get())
                                .isEqualTo("Bearer customer-test-token");
        }

        @Test
        void shouldNotExposeInternalRoutes()
                        throws Exception {

                String[] internalPaths = {
                                "/internal/transfers",
                                "/internal/ledger",
                                "/internal/recipient-accounts/BANK-B-RECIPIENT-001",
                                "/score",
                                "/train"
                };

                for (String path : internalPaths) {
                        HttpResponse<String> response = sendGet(path);

                        assertThat(response.statusCode())
                                        .as("Path should not be exposed: %s", path)
                                        .isEqualTo(403);
                }

                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldForwardReconciliationReadForOperationsWithoutChangingPathOrAuthorization()
                        throws Exception {

                UPSTREAM_STATUS.set(200);
                UPSTREAM_RESPONSE_BODY.set(
                                """
                                                {
                                                  "runId": "11111111-1111-1111-1111-111111111111",
                                                  "status": "COMPLETED"
                                                }
                                                """);

                String path = "/api/v1/reconciliation/runs/11111111-1111-1111-1111-111111111111";

                HttpResponse<String> response = sendGet(
                                path,
                                "operations-test-token");

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("COMPLETED");

                assertThat(REQUEST_COUNT.get()).isEqualTo(1);
                assertThat(LAST_METHOD.get()).isEqualTo("GET");
                assertThat(LAST_PATH.get()).isEqualTo(path);
                assertThat(LAST_AUTHORIZATION.get())
                                .isEqualTo("Bearer operations-test-token");
        }

        @Test
        void shouldForwardManualReconciliationForAdminWithoutChangingPathOrAuthorization()
                        throws Exception {

                UPSTREAM_STATUS.set(200);
                UPSTREAM_RESPONSE_BODY.set(
                                """
                                                {
                                                  "runId": "22222222-2222-2222-2222-222222222222",
                                                  "status": "COMPLETED"
                                                }
                                                """);

                String requestBody = """
                                {
                                  "businessDate": "2026-10-20"
                                }
                                """;

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(
                                                gatewayBaseUrl() + "/api/v1/reconciliation/run"))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer admin-test-token")
                                .timeout(Duration.ofSeconds(5))
                                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                .build();

                HttpResponse<String> response = httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("COMPLETED");

                assertThat(REQUEST_COUNT.get()).isEqualTo(1);
                assertThat(LAST_METHOD.get()).isEqualTo("POST");
                assertThat(LAST_PATH.get()).isEqualTo("/api/v1/reconciliation/run");
                assertThat(LAST_BODY.get()).isEqualTo(requestBody);
                assertThat(LAST_AUTHORIZATION.get())
                                .isEqualTo("Bearer admin-test-token");
        }

        @Test
        void shouldRejectCustomerFromReconciliationRoutes()
                        throws Exception {

                HttpResponse<String> response = sendGet(
                                "/api/v1/reconciliation/runs/11111111-1111-1111-1111-111111111111",
                                "customer-test-token");

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldRejectOperationsRoleFromAnotherClient()
                        throws Exception {

                HttpResponse<String> response = sendGet(
                                "/api/v1/reconciliation/runs/11111111-1111-1111-1111-111111111111",
                                "foreign-operations-test-token");

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldRejectReconciliationRequestWithoutBearerToken()
                        throws Exception {

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(
                                                gatewayBaseUrl()
                                                                + "/api/v1/reconciliation/runs/11111111-1111-1111-1111-111111111111"))
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build();

                HttpResponse<String> response = httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(401);
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

        @Test
        void shouldRejectTransferRequestWithoutBearerToken()
                        throws Exception {

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(gatewayBaseUrl() + "/api/v1/transfers"))
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build();

                HttpResponse<String> response = httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldRejectAuthenticatedUserWithoutCustomerRole()
                        throws Exception {

                HttpResponse<String> response = sendGet(
                                "/api/v1/transfers",
                                "operations-test-token");

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldRejectTokenFromAnotherClient()
                        throws Exception {

                HttpResponse<String> response = sendGet(
                                "/api/v1/transfers",
                                "foreign-client-test-token");

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldForwardOwnAccountsRequestWithoutChangingPathOrAuthorization()
                        throws Exception {

                UPSTREAM_STATUS.set(200);
                UPSTREAM_RESPONSE_BODY.set(
                                """
                                                {
                                                  "content": [
                                                    {
                                                      "accountId": "11111111-1111-1111-1111-111111111111",
                                                      "accountNumber": "BANK-A-001",
                                                      "balance": 1000.00,
                                                      "currency": "TRY",
                                                      "status": "ACTIVE"
                                                    }
                                                  ],
                                                  "page": 0,
                                                  "size": 20,
                                                  "totalElements": 1,
                                                  "totalPages": 1
                                                }
                                                """);

                String path = "/api/v1/accounts?page=0&size=20";

                HttpResponse<String> response = sendGet(
                                path,
                                "customer-test-token");

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("BANK-A-001");

                assertThat(REQUEST_COUNT.get()).isEqualTo(1);
                assertThat(LAST_METHOD.get()).isEqualTo("GET");
                assertThat(LAST_PATH.get()).isEqualTo(path);
                assertThat(LAST_AUTHORIZATION.get())
                                .isEqualTo("Bearer customer-test-token");
        }

        @Test
        void shouldRejectOperationsFromAccountRoutes()
                        throws Exception {

                HttpResponse<String> response = sendGet(
                                "/api/v1/accounts",
                                "operations-test-token");

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldRejectAccountRequestWithoutBearerToken()
                        throws Exception {

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(gatewayBaseUrl() + "/api/v1/accounts"))
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build();

                HttpResponse<String> response = httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldRejectForeignClientFromAccountRoutes()
                        throws Exception {

                HttpResponse<String> response = sendGet(
                                "/api/v1/accounts",
                                "foreign-client-test-token");

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldRejectNonGetAccountRoute()
                        throws Exception {

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(gatewayBaseUrl() + "/api/v1/accounts"))
                                .header("Authorization", "Bearer customer-test-token")
                                .timeout(Duration.ofSeconds(5))
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build();

                HttpResponse<String> response = httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldForwardRecipientLookupWithoutChangingPathOrAuthorization()
                        throws Exception {

                UPSTREAM_STATUS.set(200);
                UPSTREAM_RESPONSE_BODY.set(
                                """
                                                {
                                                  "accountNumber": "BANK-B-RECIPIENT-001",
                                                  "currency": "TRY",
                                                  "status": "ACTIVE"
                                                }
                                                """);

                String path = "/api/v1/recipients/BANK-B-RECIPIENT-001";

                HttpResponse<String> response = sendGet(
                                path,
                                "customer-test-token");

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("BANK-B-RECIPIENT-001");

                assertThat(REQUEST_COUNT.get()).isEqualTo(1);
                assertThat(LAST_METHOD.get()).isEqualTo("GET");
                assertThat(LAST_PATH.get()).isEqualTo(path);
                assertThat(LAST_AUTHORIZATION.get())
                                .isEqualTo("Bearer customer-test-token");
        }

        @Test
        void shouldRejectOperationsFromRecipientRoutes()
                        throws Exception {

                HttpResponse<String> response = sendGet(
                                "/api/v1/recipients/BANK-B-RECIPIENT-001",
                                "operations-test-token");

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldRejectForeignClientFromRecipientRoutes()
                        throws Exception {

                HttpResponse<String> response = sendGet(
                                "/api/v1/recipients/BANK-B-RECIPIENT-001",
                                "foreign-client-test-token");

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldRejectRecipientRequestWithoutBearerToken()
                        throws Exception {

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(
                                                gatewayBaseUrl()
                                                                + "/api/v1/recipients/BANK-B-RECIPIENT-001"))
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build();

                HttpResponse<String> response = httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        @Test
        void shouldRejectNonGetRecipientRoute()
                        throws Exception {

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(
                                                gatewayBaseUrl()
                                                                + "/api/v1/recipients/BANK-B-RECIPIENT-001"))
                                .header("Authorization", "Bearer customer-test-token")
                                .timeout(Duration.ofSeconds(5))
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build();

                HttpResponse<String> response = httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(REQUEST_COUNT.get()).isZero();
        }

        private HttpResponse<String> sendGet(String path) throws Exception {
                return sendGet(path, "customer-test-token");
        }

        private HttpResponse<String> sendGet(
                        String path,
                        String token) throws Exception {

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(gatewayBaseUrl() + path))
                                .header("Authorization", "Bearer " + token)
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build();

                return httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString());
        }

        private HttpResponse<String> sendCreateTransfer(
                        String token,
                        String spoofedUserId) throws Exception {

                String requestBody = """
                                {
                                  "amount": 150.00,
                                  "currency": "TRY",
                                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                                  "toAccountNumber": "BANK-B-RECIPIENT-001",
                                  "idempotencyKey": "rate-limit-integration-test"
                                }
                                """;

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                                .uri(URI.create(
                                                gatewayBaseUrl() + "/api/v1/transfers"))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer " + token)
                                .timeout(Duration.ofSeconds(5));

                if (spoofedUserId != null) {
                        requestBuilder.header("X-User-Id", spoofedUserId);
                }

                HttpRequest request = requestBuilder
                                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
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
                LAST_AUTHORIZATION.set(
                                exchange.getRequestHeaders().getFirst("Authorization"));

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

        @Test
        void shouldEnforceBurstCapacityForTransferCreation()
                        throws Exception {

                HttpResponse<String> first = sendCreateTransfer(
                                "customer-test-token",
                                null);

                HttpResponse<String> second = sendCreateTransfer(
                                "customer-test-token",
                                null);

                HttpResponse<String> third = sendCreateTransfer(
                                "customer-test-token",
                                null);

                assertThat(first.statusCode()).isEqualTo(201);
                assertThat(second.statusCode()).isEqualTo(201);

                assertThat(third.statusCode()).isEqualTo(429);

                assertThat(REQUEST_COUNT.get()).isEqualTo(2);
        }

        @Test
        void shouldIsolateRateLimitBucketsByJwtSubject()
                        throws Exception {

                HttpResponse<String> firstUserFirstRequest = sendCreateTransfer(
                                "customer-test-token",
                                "spoofed-user-1");

                HttpResponse<String> firstUserSecondRequest = sendCreateTransfer(
                                "customer-alias-test-token",
                                "spoofed-user-2");

                HttpResponse<String> firstUserThirdRequest = sendCreateTransfer(
                                "customer-test-token",
                                "another-spoofed-user");

                HttpResponse<String> secondUserFirstRequest = sendCreateTransfer(
                                "second-customer-test-token",
                                "spoofed-user-1");

                HttpResponse<String> firstUserAfterSecondUser = sendCreateTransfer(
                                "customer-alias-test-token",
                                "spoofed-user-3");

                assertThat(firstUserFirstRequest.statusCode()).isEqualTo(201);
                assertThat(firstUserSecondRequest.statusCode()).isEqualTo(201);

                // Same subject must share the same bucket.
                assertThat(firstUserThirdRequest.statusCode()).isEqualTo(429);

                // A different JWT subject must have an independent bucket.
                assertThat(secondUserFirstRequest.statusCode()).isEqualTo(201);

                // Client-controlled headers must not bypass the original user's limit.
                assertThat(firstUserAfterSecondUser.statusCode()).isEqualTo(429);

                assertThat(REQUEST_COUNT.get()).isEqualTo(3);
        }

        @Test
        void shouldRejectTransferCreationWhenRedisIsUnavailable() throws Exception {
                var pauseResult = REDIS.execInContainer(
                                "redis-cli",
                                "-a",
                                REDIS_TEST_PASSWORD,
                                "CLIENT",
                                "PAUSE",
                                "10000",
                                "ALL");

                assertThat(pauseResult.getExitCode()).isZero();

                HttpResponse<String> response;

                try {
                        response = sendCreateTransfer("customer-test-token", null);
                } finally {
                        var unpauseResult = REDIS.execInContainer(
                                        "redis-cli",
                                        "-a",
                                        REDIS_TEST_PASSWORD,
                                        "CLIENT",
                                        "UNPAUSE");

                        assertThat(unpauseResult.getExitCode()).isZero();
                }

                assertThat(response.statusCode()).isEqualTo(503);
                assertThat(REQUEST_COUNT.get()).isZero();
        }
}