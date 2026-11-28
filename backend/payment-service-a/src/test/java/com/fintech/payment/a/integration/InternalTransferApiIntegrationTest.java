package com.fintech.payment.a.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintech.payment.a.support.PaymentATestSecurityConfiguration;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.kafka.enabled=false",
                "app.outbox.relay.enabled=false"
        })
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PaymentATestSecurityConfiguration.class)
class InternalTransferApiIntegrationTest {

    private static final String CUSTOMER_A_TOKEN =
            "customer-a-test-token";

    private static final String CUSTOMER_B_TOKEN =
            "customer-b-test-token";

    private static final String CUSTOMER_NO_ROLE_TOKEN =
            "customer-no-role-test-token";

    private static final String FOREIGN_CLIENT_TOKEN =
            "foreign-client-test-token";

    private static final UUID SOURCE_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID DESTINATION_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final UUID FOREIGN_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static final UUID LOW_BALANCE_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000004");

    private static final UUID FROZEN_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000005");

    private static final UUID USD_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000006");

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("payment_a_internal_transfer_api_test_db")
                    .withUsername("payment_a_internal_transfer_api_user")
                    .withPassword("payment_a_internal_transfer_api_password");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TestRestTemplate restTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM internal_transfers");
        jdbcTemplate.update("DELETE FROM inbox_messages");
        jdbcTemplate.update("DELETE FROM ledger_entries");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM accounts");

        insertAccount(
                SOURCE_ACCOUNT_ID,
                "INTERNAL-SOURCE-001",
                "customer-a-subject",
                "1000.00",
                "TRY",
                "ACTIVE");

        insertAccount(
                DESTINATION_ACCOUNT_ID,
                "INTERNAL-DESTINATION-001",
                "customer-a-subject",
                "250.00",
                "TRY",
                "ACTIVE");

        insertAccount(
                FOREIGN_ACCOUNT_ID,
                "INTERNAL-FOREIGN-001",
                "customer-b-subject",
                "500.00",
                "TRY",
                "ACTIVE");

        insertAccount(
                LOW_BALANCE_ACCOUNT_ID,
                "INTERNAL-LOW-BALANCE-001",
                "customer-a-subject",
                "5.00",
                "TRY",
                "ACTIVE");

        insertAccount(
                FROZEN_ACCOUNT_ID,
                "INTERNAL-FROZEN-001",
                "customer-a-subject",
                "150.00",
                "TRY",
                "FROZEN");

        insertAccount(
                USD_ACCOUNT_ID,
                "INTERNAL-USD-001",
                "customer-a-subject",
                "150.00",
                "USD",
                "ACTIVE");
    }

    @Test
    void shouldTransferMoneyBetweenOwnAccountsAtomically() {
        ResponseEntity<JsonNode> response = post(
                CUSTOMER_A_TOKEN,
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                "100.00",
                "TRY",
                "internal-transfer-success-001");

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getBody()).isNotNull();

        JsonNode body = response.getBody();

        assertThat(body.path("status").asText())
                .isEqualTo("COMPLETED");

        assertThat(body.path("replayed").asBoolean())
                .isFalse();

        String transferId = body.path("transferId").asText();
        String correlationId = body.path("correlationId").asText();

        assertThat(transferId).isNotBlank();
        assertThat(correlationId).isNotBlank();

        assertThat(balance(SOURCE_ACCOUNT_ID))
                .isEqualByComparingTo("900.00");

        assertThat(balance(DESTINATION_ACCOUNT_ID))
                .isEqualByComparingTo("350.00");

        assertThat(count("internal_transfers"))
                .isEqualTo(1L);

        assertThat(count("ledger_entries"))
                .isEqualTo(2L);

        assertThat(countLedgerType(transferId, "DEBIT"))
                .isEqualTo(1L);

        assertThat(countLedgerType(transferId, "CREDIT"))
                .isEqualTo(1L);

        assertThat(countLedgerForTransfer(
                transferId,
                correlationId))
                .isEqualTo(2L);

        assertThat(ledgerBalance(
                transferId,
                "DEBIT",
                "balance_before"))
                .isEqualByComparingTo("1000.00");

        assertThat(ledgerBalance(
                transferId,
                "DEBIT",
                "balance_after"))
                .isEqualByComparingTo("900.00");

        assertThat(ledgerBalance(
                transferId,
                "CREDIT",
                "balance_before"))
                .isEqualByComparingTo("250.00");

        assertThat(ledgerBalance(
                transferId,
                "CREDIT",
                "balance_after"))
                .isEqualByComparingTo("350.00");
    }

    @Test
    void shouldReturnExistingTransferForDuplicateRequest() {
        String idempotencyKey = "internal-transfer-duplicate-001";

        ResponseEntity<JsonNode> first = post(
                CUSTOMER_A_TOKEN,
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                "100.00",
                "TRY",
                idempotencyKey);

        ResponseEntity<JsonNode> second = post(
                CUSTOMER_A_TOKEN,
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                "100.00",
                "TRY",
                idempotencyKey);

        assertThat(first.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(second.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(first.getBody()).isNotNull();
        assertThat(second.getBody()).isNotNull();

        assertThat(second.getBody().path("replayed").asBoolean())
                .isTrue();

        assertThat(second.getBody().path("transferId").asText())
                .isEqualTo(first.getBody().path("transferId").asText());

        assertThat(balance(SOURCE_ACCOUNT_ID))
                .isEqualByComparingTo("900.00");

        assertThat(balance(DESTINATION_ACCOUNT_ID))
                .isEqualByComparingTo("350.00");

        assertThat(count("internal_transfers"))
                .isEqualTo(1L);

        assertThat(count("ledger_entries"))
                .isEqualTo(2L);
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentContent() {
        String idempotencyKey = "internal-transfer-conflict-001";

        ResponseEntity<JsonNode> first = post(
                CUSTOMER_A_TOKEN,
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                "100.00",
                "TRY",
                idempotencyKey);

        ResponseEntity<JsonNode> second = post(
                CUSTOMER_A_TOKEN,
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                "125.00",
                "TRY",
                idempotencyKey);

        assertThat(first.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(second.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(balance(SOURCE_ACCOUNT_ID))
                .isEqualByComparingTo("900.00");

        assertThat(balance(DESTINATION_ACCOUNT_ID))
                .isEqualByComparingTo("350.00");

        assertThat(count("internal_transfers"))
                .isEqualTo(1L);

        assertThat(count("ledger_entries"))
                .isEqualTo(2L);
    }

    @Test
    void shouldNotTransferToAnotherCustomersAccount() {
        ResponseEntity<JsonNode> response = post(
                CUSTOMER_A_TOKEN,
                SOURCE_ACCOUNT_ID,
                FOREIGN_ACCOUNT_ID,
                "100.00",
                "TRY",
                "internal-transfer-foreign-account-001");

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(balance(SOURCE_ACCOUNT_ID))
                .isEqualByComparingTo("1000.00");

        assertThat(balance(FOREIGN_ACCOUNT_ID))
                .isEqualByComparingTo("500.00");

        assertThat(count("internal_transfers"))
                .isZero();

        assertThat(count("ledger_entries"))
                .isZero();
    }

    @Test
    void shouldExposeInternalTransferEntriesInBothAccountLedgers() {
        ResponseEntity<JsonNode> transferResponse = post(
                CUSTOMER_A_TOKEN,
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                "100.00",
                "TRY",
                "internal-transfer-ledger-visibility-001");

        assertThat(transferResponse.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(transferResponse.getBody()).isNotNull();

        String transferId = transferResponse.getBody()
                .path("transferId")
                .asText();

        ResponseEntity<JsonNode> sourceLedger = getAccountLedger(
                CUSTOMER_A_TOKEN,
                SOURCE_ACCOUNT_ID);

        ResponseEntity<JsonNode> destinationLedger = getAccountLedger(
                CUSTOMER_A_TOKEN,
                DESTINATION_ACCOUNT_ID);

        assertThat(sourceLedger.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(destinationLedger.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(sourceLedger.getBody()).isNotNull();
        assertThat(destinationLedger.getBody()).isNotNull();

        JsonNode debitEntry = sourceLedger.getBody()
                .path("content")
                .get(0);

        JsonNode creditEntry = destinationLedger.getBody()
                .path("content")
                .get(0);

        assertThat(debitEntry.path("entryType").asText())
                .isEqualTo("DEBIT");

        assertThat(creditEntry.path("entryType").asText())
                .isEqualTo("CREDIT");

        assertThat(debitEntry.path("transferId").asText())
                .isEqualTo(transferId);

        assertThat(creditEntry.path("transferId").asText())
                .isEqualTo(transferId);

        assertThat(debitEntry.path("amount").decimalValue())
                .isEqualByComparingTo("100.00");

        assertThat(creditEntry.path("amount").decimalValue())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRejectTransferWhenSourceBalanceIsInsufficient() {
        ResponseEntity<JsonNode> response = post(
                CUSTOMER_A_TOKEN,
                LOW_BALANCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                "100.00",
                "TRY",
                "internal-transfer-insufficient-001");

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(balance(LOW_BALANCE_ACCOUNT_ID))
                .isEqualByComparingTo("5.00");

        assertThat(balance(DESTINATION_ACCOUNT_ID))
                .isEqualByComparingTo("250.00");

        assertThat(count("internal_transfers"))
                .isZero();

        assertThat(count("ledger_entries"))
                .isZero();
    }

    @Test
    void shouldRollbackDebitWhenDestinationAccountIsFrozen() {
        ResponseEntity<JsonNode> response = post(
                CUSTOMER_A_TOKEN,
                SOURCE_ACCOUNT_ID,
                FROZEN_ACCOUNT_ID,
                "100.00",
                "TRY",
                "internal-transfer-frozen-target-001");

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(balance(SOURCE_ACCOUNT_ID))
                .isEqualByComparingTo("1000.00");

        assertThat(balance(FROZEN_ACCOUNT_ID))
                .isEqualByComparingTo("150.00");

        assertThat(count("internal_transfers"))
                .isZero();

        assertThat(count("ledger_entries"))
                .isZero();
    }

    @Test
    void shouldRejectTransferWhenCurrenciesDoNotMatch() {
        ResponseEntity<JsonNode> response = post(
                CUSTOMER_A_TOKEN,
                SOURCE_ACCOUNT_ID,
                USD_ACCOUNT_ID,
                "100.00",
                "TRY",
                "internal-transfer-currency-mismatch-001");

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(balance(SOURCE_ACCOUNT_ID))
                .isEqualByComparingTo("1000.00");

        assertThat(balance(USD_ACCOUNT_ID))
                .isEqualByComparingTo("150.00");

        assertThat(count("internal_transfers"))
                .isZero();

        assertThat(count("ledger_entries"))
                .isZero();
    }

    @Test
    void shouldRejectTransferBetweenSameAccount() {
        ResponseEntity<JsonNode> response = post(
                CUSTOMER_A_TOKEN,
                SOURCE_ACCOUNT_ID,
                SOURCE_ACCOUNT_ID,
                "100.00",
                "TRY",
                "internal-transfer-same-account-001");

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(balance(SOURCE_ACCOUNT_ID))
                .isEqualByComparingTo("1000.00");

        assertThat(count("internal_transfers"))
                .isZero();

        assertThat(count("ledger_entries"))
                .isZero();
    }

    @Test
    void shouldRejectRequestWithoutBearerToken() {
        ResponseEntity<JsonNode> response = post(
                null,
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                "100.00",
                "TRY",
                "internal-transfer-no-token-001");

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectCustomerWithoutCustomerRole() {
        ResponseEntity<JsonNode> response = post(
                CUSTOMER_NO_ROLE_TOKEN,
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                "100.00",
                "TRY",
                "internal-transfer-no-role-001");

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldRejectRoleFromAnotherClient() {
        ResponseEntity<JsonNode> response = post(
                FOREIGN_CLIENT_TOKEN,
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                "100.00",
                "TRY",
                "internal-transfer-foreign-client-001");

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldProcessConcurrentDuplicateRequestsExactlyOnce()
            throws Exception {

        int requestCount = 5;

        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);

        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<ResponseEntity<JsonNode>>> futures =
                new ArrayList<>();

        try {
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Concurrent test start timed out");
                    }

                    return post(
                            CUSTOMER_A_TOKEN,
                            SOURCE_ACCOUNT_ID,
                            DESTINATION_ACCOUNT_ID,
                            "100.00",
                            "TRY",
                            "internal-transfer-concurrent-001");
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .isTrue();

            start.countDown();

            List<ResponseEntity<JsonNode>> responses =
                    new ArrayList<>();

            for (Future<ResponseEntity<JsonNode>> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(responses)
                    .allSatisfy(response -> {
                        assertThat(response.getStatusCode())
                                .isIn(
                                        HttpStatus.CREATED,
                                        HttpStatus.OK);

                        assertThat(response.getBody())
                                .isNotNull();
                    });

            Set<String> transferIds = new HashSet<>();

            for (ResponseEntity<JsonNode> response : responses) {
                transferIds.add(
                        response.getBody()
                                .path("transferId")
                                .asText());
            }

            assertThat(transferIds)
                    .hasSize(1);

            assertThat(balance(SOURCE_ACCOUNT_ID))
                    .isEqualByComparingTo("900.00");

            assertThat(balance(DESTINATION_ACCOUNT_ID))
                    .isEqualByComparingTo("350.00");

            assertThat(count("internal_transfers"))
                    .isEqualTo(1L);

            assertThat(count("ledger_entries"))
                    .isEqualTo(2L);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private ResponseEntity<JsonNode> post(
            String token,
            UUID fromAccountId,
            UUID toAccountId,
            String amount,
            String currency,
            String idempotencyKey) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (token != null) {
            headers.setBearerAuth(token);
        }

        Map<String, Object> body = Map.of(
                "amount", new BigDecimal(amount),
                "currency", currency,
                "fromAccountId", fromAccountId,
                "toAccountId", toAccountId,
                "idempotencyKey", idempotencyKey);

        return restTemplate.exchange(
                "http://localhost:" + port
                        + "/api/v1/accounts/internal-transfers",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class);
    }

    private ResponseEntity<JsonNode> getAccountLedger(
            String token,
            UUID accountId) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        return restTemplate.exchange(
                "http://localhost:" + port
                        + "/api/v1/accounts/" + accountId
                        + "/ledger?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class);
    }

    private void insertAccount(
            UUID accountId,
            String accountNumber,
            String ownerSubject,
            String balance,
            String currency,
            String status) {

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
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                accountId,
                accountNumber,
                ownerSubject,
                new BigDecimal(balance),
                currency,
                status);
    }

    private BigDecimal balance(UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal.class,
                accountId);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Long.class);
    }

    private long countLedgerType(
            String transferId,
            String entryType) {

        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ledger_entries
                WHERE transfer_id = ?
                  AND entry_type = ?
                """,
                Long.class,
                UUID.fromString(transferId),
                entryType);
    }

    private long countLedgerForTransfer(
            String transferId,
            String correlationId) {

        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ledger_entries
                WHERE transfer_id = ?
                  AND correlation_id = ?
                """,
                Long.class,
                UUID.fromString(transferId),
                UUID.fromString(correlationId));
    }

    private BigDecimal ledgerBalance(
            String transferId,
            String entryType,
            String columnName) {

        if (!"balance_before".equals(columnName)
                && !"balance_after".equals(columnName)) {
            throw new IllegalArgumentException(
                    "Unsupported ledger balance column");
        }

        return jdbcTemplate.queryForObject(
                """
                SELECT %s
                FROM ledger_entries
                WHERE transfer_id = ?
                  AND entry_type = ?
                """.formatted(columnName),
                BigDecimal.class,
                UUID.fromString(transferId),
                entryType);
    }
}
