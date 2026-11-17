package com.fintech.payment.a.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
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

        @BeforeEach
        void cleanDatabase() {
                jdbcTemplate.update("DELETE FROM inbox_messages");
                jdbcTemplate.update("DELETE FROM ledger_entries");
                jdbcTemplate.update("DELETE FROM outbox_events");
                jdbcTemplate.update("DELETE FROM accounts");

                insertAccount("BANK-A-001", "customer-a-subject", "1000.00");
                insertAccount("BANK-A-002", "customer-a-subject", "2000.00");
                insertAccount("BANK-A-003", "customer-a-subject", "3000.00");
                insertAccount("BANK-B-001", "customer-b-subject", "4000.00");
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
