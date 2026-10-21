package com.fintech.transfer.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InternalTransferApiIntegrationTest {

    private static final Instant FROM = Instant.parse("2026-10-20T00:00:00Z");

    private static final Instant TO = Instant.parse("2026-10-21T00:00:00Z");

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("internal_transfer_api_test_db")
            .withUsername("internal_transfer_api_test_user")
            .withPassword("internal_transfer_api_test_password");

    @DynamicPropertySource
    static void registerDataSourceProperties(
            DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID olderTransferId;
    private UUID middleTransferId;

    private UUID olderCorrelationId;
    private UUID middleCorrelationId;

    @BeforeEach
    void prepareDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM saga_state");
        jdbcTemplate.update("DELETE FROM inbox_messages");
        jdbcTemplate.update("DELETE FROM transfers");

        olderTransferId = UUID.randomUUID();
        middleTransferId = UUID.randomUUID();

        olderCorrelationId = UUID.randomUUID();
        middleCorrelationId = UUID.randomUUID();

        /*
         * Oluşturulma zamanı sorgu aralığından önce.
         * debitCompletedAt ve updatedAt aralık içinde.
         * Bu kayıt sorguda bulunmalı.
         */
        insertTransfer(new TransferFixture(
                olderTransferId,
                olderCorrelationId,
                "DEBIT_COMPLETED",
                "internal-test-older-" + olderTransferId,
                Instant.parse("2026-10-19T12:00:00Z"),
                Instant.parse("2026-10-20T10:00:00Z"),
                Instant.parse("2026-10-19T12:00:00Z"),
                Instant.parse("2026-10-19T12:00:01Z"),
                Instant.parse("2026-10-20T10:00:00Z"),
                null,
                null,
                null));

        /*
         * Oluşturulma ve yaşam döngüsü zamanları aralık içinde.
         * Bu kayıt da sonuçlarda bulunmalı.
         */
        insertTransfer(new TransferFixture(
                middleTransferId,
                middleCorrelationId,
                "DEBIT_REQUESTED",
                "internal-test-middle-" + middleTransferId,
                FROM,
                FROM.plusSeconds(1),
                FROM,
                FROM.plusSeconds(1),
                null,
                null,
                null,
                null));

        /*
         * Bu kaydın creditCompletedAt ve completedAt değerleri
         * tam olarak TO sınırında. Bitiş hariç olduğundan dönmemeli.
         */
        Instant beforeRange = Instant.parse("2026-10-19T14:00:00Z");

        insertTransfer(new TransferFixture(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "COMPLETED",
                "internal-test-boundary-" + UUID.randomUUID(),
                beforeRange,
                TO,
                beforeRange,
                beforeRange.plusSeconds(1),
                beforeRange.plusSeconds(2),
                beforeRange.plusSeconds(3),
                TO,
                TO));
    }

    @Test
    void shouldReturnTransfersByLifecycleRangeWithStablePagination() {
        JsonNode firstPage = getTransfers(FROM, TO, 0, 1);

        assertThat(firstPage.isArray()).isTrue();
        assertThat(firstPage.size()).isEqualTo(1);

        assertThat(firstPage.get(0).path("transferId").asText())
                .isEqualTo(olderTransferId.toString());

        assertThat(firstPage.get(0).path("correlationId").asText())
                .isEqualTo(olderCorrelationId.toString());

        assertThat(firstPage.get(0).path("status").asText())
                .isEqualTo("DEBIT_COMPLETED");

        assertThat(firstPage.get(0).path("amount").decimalValue()
                .compareTo(new BigDecimal("10.00")))
                .isZero();

        assertThat(firstPage.get(0).path("currency").asText())
                .isEqualTo("TRY");

        JsonNode secondPage = getTransfers(FROM, TO, 1, 1);

        assertThat(secondPage.isArray()).isTrue();
        assertThat(secondPage.size()).isEqualTo(1);

        assertThat(secondPage.get(0).path("transferId").asText())
                .isEqualTo(middleTransferId.toString());

        assertThat(secondPage.get(0).path("correlationId").asText())
                .isEqualTo(middleCorrelationId.toString());

        JsonNode thirdPage = getTransfers(FROM, TO, 2, 1);

        assertThat(thirdPage.isArray()).isTrue();
        assertThat(thirdPage.size()).isZero();
    }

    @Test
    void shouldRejectInvalidDateRange() {
        URI uri = buildUri(TO, FROM, 0, 10);

        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectPageSizeAboveMaximum() {
        URI uri = buildUri(FROM, TO, 0, 1001);

        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private JsonNode getTransfers(
            Instant from,
            Instant to,
            int page,
            int size) {

        URI uri = buildUri(from, to, page, size);

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(uri, JsonNode.class);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).isNotNull();

        return response.getBody();
    }

    private URI buildUri(
            Instant from,
            Instant to,
            int page,
            int size) {

        return UriComponentsBuilder
                .fromUriString(
                        "http://localhost:" + port + "/internal/transfers")
                .queryParam("from", from.toString())
                .queryParam("to", to.toString())
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .encode()
                .toUri();
    }

    private void insertTransfer(TransferFixture fixture) {
        jdbcTemplate.update(
                """
                        INSERT INTO transfers (
                            id,
                            correlation_id,
                            from_account_id,
                            to_account_id,
                            amount,
                            currency,
                            status,
                            failure_reason,
                            idempotency_key,
                            started_at,
                            debit_requested_at,
                            debit_completed_at,
                            credit_requested_at,
                            credit_completed_at,
                            completed_at,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            ?, ?, ?, ?, 10.00, 'TRY', ?, NULL, ?,
                            ?, ?, ?, ?, ?, ?, ?, ?
                        )
                        """,
                fixture.id(),
                fixture.correlationId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                fixture.status(),
                fixture.idempotencyKey(),
                toTimestamp(fixture.startedAt()),
                toTimestamp(fixture.debitRequestedAt()),
                toTimestamp(fixture.debitCompletedAt()),
                toTimestamp(fixture.creditRequestedAt()),
                toTimestamp(fixture.creditCompletedAt()),
                toTimestamp(fixture.completedAt()),
                toTimestamp(fixture.createdAt()),
                toTimestamp(fixture.updatedAt()));
    }

    private Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private record TransferFixture(
            UUID id,
            UUID correlationId,
            String status,
            String idempotencyKey,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant debitRequestedAt,
            Instant debitCompletedAt,
            Instant creditRequestedAt,
            Instant creditCompletedAt,
            Instant completedAt) {
    }
}