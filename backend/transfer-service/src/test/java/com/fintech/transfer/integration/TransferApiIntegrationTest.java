package com.fintech.transfer.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.transfer.support.TransferTestSecurityConfiguration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TransferTestSecurityConfiguration.class)
class TransferApiIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transfer_api_test_db")
            .withUsername("transfer_api_test_user")
            .withPassword("transfer_api_test_password");

    @DynamicPropertySource
    static void registerDataSourceProperties(
            DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl);

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername);

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateTransfer() {
        String idempotencyKey = "integration-" + UUID.randomUUID();

        String request = """
                {
                  "amount": 1500.00,
                  "currency": "TRY",
                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                  "toAccountId": "22222222-2222-2222-2222-222222222222",
                  "idempotencyKey": "%s"
                }
                """.formatted(idempotencyKey);

        ResponseEntity<String> response = postTransfer(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getBody())
                .contains("\"status\":\"DEBIT_REQUESTED\"")
                .contains("\"idempotencyKey\":\""
                        + idempotencyKey + "\"");
    }

    @Test
    void shouldReturnExistingTransferForDuplicateIdempotencyKey() {
        String idempotencyKey = "duplicate-" + UUID.randomUUID();

        String request = """
                {
                  "amount": 1000.00,
                  "currency": "TRY",
                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                  "toAccountId": "22222222-2222-2222-2222-222222222222",
                  "idempotencyKey": "%s"
                }
                """.formatted(idempotencyKey);

        ResponseEntity<String> first = postTransfer(request);

        ResponseEntity<String> second = postTransfer(request);

        assertThat(first.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(second.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        DocumentContext firstContext = JsonPath.parse(first.getBody());
        DocumentContext secondContext = JsonPath.parse(second.getBody());

        assertThat(secondContext.read("$.transferId", String.class))
                .isEqualTo(firstContext.read("$.transferId", String.class));

        assertThat(secondContext.read("$.status", String.class))
                .isEqualTo("DEBIT_REQUESTED");
    }

    @Test
    void shouldRejectSameSourceAndTargetAccount() {
        String request = """
                {
                  "amount": 1000.00,
                  "currency": "TRY",
                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                  "toAccountId": "11111111-1111-1111-1111-111111111111",
                  "idempotencyKey": "same-account-%s"
                }
                """.formatted(UUID.randomUUID());

        ResponseEntity<String> response = postTransfer(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldPersistTransferSagaStateAndInitialCommand() throws Exception {
        String idempotencyKey = "outbox-integration-" + UUID.randomUUID();

        String request = """
                {
                  "amount": 1500.00,
                  "currency": "TRY",
                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                  "toAccountId": "22222222-2222-2222-2222-222222222222",
                  "idempotencyKey": "%s"
                }
                """.formatted(idempotencyKey);

        ResponseEntity<String> response = postTransfer(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        UUID transferId = UUID.fromString(
                objectMapper.readTree(response.getBody())
                        .get("transferId")
                        .asText());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM transfers WHERE id = ?",
                String.class,
                transferId);

        Long sagaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saga_state WHERE transfer_id = ?",
                Long.class,
                transferId);

        Long outboxCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM outbox_events
                        WHERE aggregate_id = ?
                          AND event_type = 'debit-requested'
                          AND status = 'PENDING'
                        """,
                Long.class,
                transferId);

        String outboxOwnerSubject = jdbcTemplate.queryForObject(
                """
                        SELECT payload ->> 'ownerSubject'
                        FROM outbox_events
                        WHERE aggregate_id = ?
                          AND event_type = 'debit-requested'
                        """,
                String.class,
                transferId);

        assertThat(status).isEqualTo("DEBIT_REQUESTED");
        assertThat(sagaCount).isEqualTo(1L);
        assertThat(outboxCount).isEqualTo(1L);
        assertThat(outboxOwnerSubject).isEqualTo("customer-user-1");
    }

    @Test
    void shouldScopeIdempotencyKeyPerCustomerOwner() {
        String sharedIdempotencyKey = "scoped-idempotency-" + UUID.randomUUID();

        String request = """
                {
                  "amount": 2500.00,
                  "currency": "TRY",
                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                  "toAccountId": "22222222-2222-2222-2222-222222222222",
                  "idempotencyKey": "%s"
                }
                """.formatted(sharedIdempotencyKey);

        ResponseEntity<String> customer1Create = postTransfer(request, "customer-test-token");
        assertThat(customer1Create.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String customer1TransferId = JsonPath.parse(customer1Create.getBody())
                .read("$.transferId", String.class);

        String persistedOwner1 = jdbcTemplate.queryForObject(
                "SELECT owner_subject FROM transfers WHERE id = ?",
                String.class,
                UUID.fromString(customer1TransferId));
        assertThat(persistedOwner1).isEqualTo("customer-user-1");

        ResponseEntity<String> customer1Retry = postTransfer(request, "customer-test-token");
        assertThat(customer1Retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.parse(customer1Retry.getBody()).read("$.transferId", String.class))
                .isEqualTo(customer1TransferId);

        ResponseEntity<String> customer2Create = postTransfer(request, "another-customer-test-token");
        assertThat(customer2Create.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String customer2TransferId = JsonPath.parse(customer2Create.getBody())
                .read("$.transferId", String.class);
        assertThat(customer2TransferId).isNotEqualTo(customer1TransferId);

        String persistedOwner2 = jdbcTemplate.queryForObject(
                "SELECT owner_subject FROM transfers WHERE id = ?",
                String.class,
                UUID.fromString(customer2TransferId));
        assertThat(persistedOwner2).isEqualTo("customer-user-2");
    }

    @Test
    void shouldEnforceOwnerAccessWhenReadingTransfers() {
        String idempotencyKey = "owner-read-" + UUID.randomUUID();

        String request = """
                {
                  "amount": 1750.00,
                  "currency": "TRY",
                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                  "toAccountId": "22222222-2222-2222-2222-222222222222",
                  "idempotencyKey": "%s"
                }
                """.formatted(idempotencyKey);

        ResponseEntity<String> createResponse = postTransfer(request, "customer-test-token");
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID transferId = UUID.fromString(
                JsonPath.parse(createResponse.getBody()).read("$.transferId", String.class));

        String persistedOwner = jdbcTemplate.queryForObject(
                "SELECT owner_subject FROM transfers WHERE id = ?",
                String.class,
                transferId);
        assertThat(persistedOwner).isEqualTo("customer-user-1");

        ResponseEntity<String> ownReadResponse = getTransfer(transferId, "customer-test-token");
        assertThat(ownReadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.parse(ownReadResponse.getBody()).read("$.transferId", String.class))
                .isEqualTo(transferId.toString());

        ResponseEntity<String> otherCustomerReadResponse = getTransfer(
                transferId,
                "another-customer-test-token");
        assertThat(otherCustomerReadResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> missingTransferReadResponse = getTransfer(
                UUID.randomUUID(),
                "another-customer-test-token");
        assertThat(missingTransferReadResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> postTransfer(String body) {
        return postTransfer(body, "customer-test-token");
    }

    private ResponseEntity<String> postTransfer(String body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        return restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/transfers",
                new HttpEntity<>(body, headers),
                String.class);
    }

    private ResponseEntity<String> getTransfer(UUID transferId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        return restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/transfers/" + transferId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }
}