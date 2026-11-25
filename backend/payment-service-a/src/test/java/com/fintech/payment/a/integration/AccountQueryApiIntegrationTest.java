package com.fintech.payment.a.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintech.payment.a.support.PaymentATestSecurityConfiguration;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
                "app.kafka.enabled=false",
                "app.outbox.relay.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PaymentATestSecurityConfiguration.class)
class AccountQueryApiIntegrationTest {

        @SuppressWarnings("resource")
        @Container
        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("payment_a_account_query_test_db")
                        .withUsername("payment_a_account_query_user")
                        .withPassword("payment_a_account_query_password");

        @DynamicPropertySource
        static void registerProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
                registry.add("spring.datasource.username", POSTGRES::getUsername);
                registry.add("spring.datasource.password", POSTGRES::getPassword);
        }

        private static final String CUSTOMER_A_TOKEN = "customer-a-test-token";
        private static final String CUSTOMER_EMPTY_TOKEN = "customer-empty-test-token";
        private static final String CUSTOMER_NO_ROLE_TOKEN = "customer-no-role-test-token";
        private static final String FOREIGN_CLIENT_TOKEN = "foreign-client-test-token";

        @LocalServerPort
        int port;

        @Autowired
        JdbcTemplate jdbcTemplate;

        @Autowired
        TestRestTemplate restTemplate;

        private UUID customerAFirstAccountId;
        private UUID customerBAccountId;

        @BeforeEach
        void cleanDatabase() {
                jdbcTemplate.update("DELETE FROM inbox_messages");
                jdbcTemplate.update("DELETE FROM ledger_entries");
                jdbcTemplate.update("DELETE FROM outbox_events");
                jdbcTemplate.update("DELETE FROM accounts");

                customerAFirstAccountId = insertAccount(
                                "BANK-A-001", "customer-a-subject", "1000.00");

                insertAccount("BANK-A-002", "customer-a-subject", "2000.00");
                insertAccount("BANK-A-003", "customer-a-subject", "3000.00");

                customerBAccountId = insertAccount(
                                "BANK-B-001", "customer-b-subject", "4000.00");
        }

        @Test
        void shouldReturnOnlyAccountsOwnedByAuthenticatedCustomer() {
                JsonNode body = getAccounts(CUSTOMER_A_TOKEN, 0, 20);

                assertThat(body.path("content")).hasSize(3);

                assertThat(body.path("content").findValuesAsText("accountNumber"))
                                .containsExactly(
                                                "BANK-A-001",
                                                "BANK-A-002",
                                                "BANK-A-003")
                                .doesNotContain("BANK-B-001");

                assertThat(body.path("content").findValuesAsText("ownerSubject"))
                                .isEmpty();

                assertThat(body.path("totalElements").asLong())
                                .isEqualTo(3);
        }

        @Test
        void shouldReturnAccountsWithStablePagination() {
                JsonNode firstPage = getAccounts(CUSTOMER_A_TOKEN, 0, 2);

                assertThat(firstPage.path("content")).hasSize(2);

                assertThat(firstPage.path("content").findValuesAsText("accountNumber"))
                                .containsExactly("BANK-A-001", "BANK-A-002");

                assertThat(firstPage.path("page").asInt()).isZero();
                assertThat(firstPage.path("size").asInt()).isEqualTo(2);
                assertThat(firstPage.path("totalElements").asLong()).isEqualTo(3);
                assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);

                JsonNode secondPage = getAccounts(CUSTOMER_A_TOKEN, 1, 2);

                assertThat(secondPage.path("content")).hasSize(1);

                assertThat(secondPage.path("content").findValuesAsText("accountNumber"))
                                .containsExactly("BANK-A-003");

                assertThat(secondPage.path("page").asInt()).isEqualTo(1);
                assertThat(secondPage.path("size").asInt()).isEqualTo(2);
                assertThat(secondPage.path("totalElements").asLong()).isEqualTo(3);
                assertThat(secondPage.path("totalPages").asInt()).isEqualTo(2);
        }

        @Test
        void shouldReturnEmptyContentWhenCustomerHasNoAccounts() {
                JsonNode body = getAccounts(CUSTOMER_EMPTY_TOKEN, 0, 20);

                assertThat(body.path("content")).isEmpty();
                assertThat(body.path("totalElements").asLong()).isZero();
                assertThat(body.path("totalPages").asInt()).isZero();
        }

        @Test
        void shouldRejectRequestWithoutBearerToken() {
                ResponseEntity<JsonNode> response = exchange(null, 0, 20);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void shouldRejectTokenWithoutCustomerRole() {
                ResponseEntity<JsonNode> response = exchange(
                                CUSTOMER_NO_ROLE_TOKEN,
                                0,
                                20);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void shouldRejectRoleFromAnotherClient() {
                ResponseEntity<JsonNode> response = exchange(
                                FOREIGN_CLIENT_TOKEN,
                                0,
                                20);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void shouldRejectPageSizeGreaterThanMaximum() {
                ResponseEntity<JsonNode> response = exchange(
                                CUSTOMER_A_TOKEN,
                                0,
                                101);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldRejectNegativePageNumber() {
                ResponseEntity<JsonNode> response = exchange(
                                CUSTOMER_A_TOKEN,
                                -1,
                                20);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldReturnOwnedAccountLedgerWithStableDescendingPagination() {
                UUID olderEntry = UUID.randomUUID();
                UUID newerEntry = UUID.randomUUID();
                UUID otherCustomerEntry = UUID.randomUUID();

                insertLedger(
                                customerAFirstAccountId,
                                Instant.parse("2026-10-20T10:00:00Z"),
                                olderEntry);

                insertLedger(
                                customerAFirstAccountId,
                                Instant.parse("2026-10-20T11:00:00Z"),
                                newerEntry);

                insertLedger(
                                customerBAccountId,
                                Instant.parse("2026-10-20T12:00:00Z"),
                                otherCustomerEntry);

                JsonNode firstPage = getAccountLedger(
                                CUSTOMER_A_TOKEN,
                                customerAFirstAccountId,
                                0,
                                1);

                assertThat(firstPage.path("content")).hasSize(1);
                assertThat(firstPage.path("content").get(0)
                                .path("correlationId").asText())
                                .isEqualTo(newerEntry.toString());

                assertThat(firstPage.path("content").get(0)
                                .path("accountId").asText())
                                .isEqualTo(customerAFirstAccountId.toString());

                assertThat(firstPage.path("page").asInt()).isZero();
                assertThat(firstPage.path("size").asInt()).isEqualTo(1);
                assertThat(firstPage.path("totalElements").asLong()).isEqualTo(2);
                assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);

                JsonNode secondPage = getAccountLedger(
                                CUSTOMER_A_TOKEN,
                                customerAFirstAccountId,
                                1,
                                1);

                assertThat(secondPage.path("content")).hasSize(1);
                assertThat(secondPage.path("content").get(0)
                                .path("correlationId").asText())
                                .isEqualTo(olderEntry.toString());
        }

        @Test
        void shouldNotRevealAnotherCustomersAccountLedger() {
                ResponseEntity<JsonNode> response = exchangeAccountLedger(
                                CUSTOMER_A_TOKEN,
                                customerBAccountId,
                                0,
                                20);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void shouldReturnNotFoundForUnknownAccount() {
                ResponseEntity<JsonNode> response = exchangeAccountLedger(
                                CUSTOMER_A_TOKEN,
                                UUID.randomUUID(),
                                0,
                                20);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void shouldRejectAccountLedgerRequestWithoutBearerToken() {
                ResponseEntity<JsonNode> response = exchangeAccountLedger(
                                null,
                                customerAFirstAccountId,
                                0,
                                20);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void shouldRejectAccountLedgerRequestWithoutCustomerRole() {
                ResponseEntity<JsonNode> response = exchangeAccountLedger(
                                CUSTOMER_NO_ROLE_TOKEN,
                                customerAFirstAccountId,
                                0,
                                20);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void shouldRejectInvalidAccountLedgerPagination() {
                ResponseEntity<JsonNode> oversized = exchangeAccountLedger(
                                CUSTOMER_A_TOKEN,
                                customerAFirstAccountId,
                                0,
                                101);

                assertThat(oversized.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST);

                ResponseEntity<JsonNode> negativePage = exchangeAccountLedger(
                                CUSTOMER_A_TOKEN,
                                customerAFirstAccountId,
                                -1,
                                20);

                assertThat(negativePage.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldExposeCustomerAccountApisOnlyInOpenApiDocument() {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(CUSTOMER_A_TOKEN);

                ResponseEntity<JsonNode> response = restTemplate.exchange(
                                "http://localhost:" + port + "/v3/api-docs",
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                JsonNode.class);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                assertThat(response.getBody()).isNotNull();

                JsonNode openApi = response.getBody();
                JsonNode paths = openApi.path("paths");

                assertThat(paths.has("/api/v1/accounts")).isTrue();

                assertThat(paths.has("/api/v1/accounts/{accountId}/ledger"))
                                .isTrue();

                assertThat(paths.has("/internal/ledger")).isFalse();

                assertThat(openApi.at(
                                "/components/securitySchemes/bearerAuth/type").asText())
                                .isEqualTo("http");

                assertThat(openApi.at(
                                "/components/securitySchemes/bearerAuth/scheme").asText())
                                .isEqualTo("bearer");
        }

        @Test
        void shouldRejectOpenApiDocumentRequestWithoutBearerToken() {
                ResponseEntity<String> response = restTemplate.getForEntity(
                                "http://localhost:" + port + "/v3/api-docs",
                                String.class);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        private JsonNode getAccountLedger(
                        String token,
                        UUID accountId,
                        int page,
                        int size) {

                ResponseEntity<JsonNode> response = exchangeAccountLedger(
                                token,
                                accountId,
                                page,
                                size);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                assertThat(response.getBody()).isNotNull();

                return response.getBody();
        }

        private ResponseEntity<JsonNode> exchangeAccountLedger(
                        String token,
                        UUID accountId,
                        int page,
                        int size) {

                HttpHeaders headers = new HttpHeaders();

                if (token != null) {
                        headers.setBearerAuth(token);
                }

                return restTemplate.exchange(
                                buildAccountLedgerUri(accountId, page, size),
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                JsonNode.class);
        }

        private URI buildAccountLedgerUri(
                        UUID accountId,
                        int page,
                        int size) {

                return UriComponentsBuilder
                                .fromHttpUrl(
                                                "http://localhost:" + port
                                                                + "/api/v1/accounts/" + accountId + "/ledger")
                                .queryParam("page", page)
                                .queryParam("size", size)
                                .build()
                                .encode()
                                .toUri();
        }

        private void insertLedger(
                        UUID accountId,
                        Instant createdAt,
                        UUID correlationId) {

                jdbcTemplate.update(
                                """
                                                INSERT INTO ledger_entries (
                                                    id,
                                                    transfer_id,
                                                    correlation_id,
                                                    account_id,
                                                    amount,
                                                    balance_before,
                                                    balance_after,
                                                    entry_type,
                                                    status,
                                                    created_at
                                                )
                                                VALUES (
                                                    ?, ?, ?, ?, 10.00, 1000.00, 990.00,
                                                    'DEBIT', 'COMPLETED', ?
                                                )
                                                """,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                correlationId,
                                accountId,
                                java.sql.Timestamp.from(createdAt));
        }

        private JsonNode getAccounts(String token, int page, int size) {
                ResponseEntity<JsonNode> response = exchange(token, page, size);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                assertThat(response.getBody()).isNotNull();

                return response.getBody();
        }

        private ResponseEntity<JsonNode> exchange(
                        String token,
                        int page,
                        int size) {
                HttpHeaders headers = new HttpHeaders();

                if (token != null) {
                        headers.setBearerAuth(token);
                }

                return restTemplate.exchange(
                                buildAccountsUri(page, size),
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                JsonNode.class);
        }

        private URI buildAccountsUri(int page, int size) {
                return UriComponentsBuilder
                                .fromHttpUrl("http://localhost:" + port + "/api/v1/accounts")
                                .queryParam("page", page)
                                .queryParam("size", size)
                                .build()
                                .encode()
                                .toUri();
        }

        private UUID insertAccount(
                        String accountNumber,
                        String ownerSubject,
                        String balance) {
                UUID accountId = UUID.randomUUID();

                jdbcTemplate.update(
                                """
                                                INSERT INTO accounts (
                                                    id,
                                                    account_number,
                                                    owner_subject,
                                                    balance,
                                                    currency,
                                                    status
                                                )
                                                VALUES (?, ?, ?, ?, 'TRY', 'ACTIVE')
                                                """,
                                accountId,
                                accountNumber,
                                ownerSubject,
                                new BigDecimal(balance));

                return accountId;
        }
}
