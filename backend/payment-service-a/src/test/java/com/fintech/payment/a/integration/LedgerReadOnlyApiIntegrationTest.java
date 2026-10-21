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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.kafka.enabled=false",
        "app.outbox.relay.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LedgerReadOnlyApiIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_a_ledger_api_test_db")
            .withUsername("payment_a_ledger_api_user")
            .withPassword("payment_a_ledger_api_password");

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

    private final Instant from = Instant.parse("2026-10-20T00:00:00Z");

    private final Instant to = Instant.parse("2026-10-21T00:00:00Z");

    private UUID accountId;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM inbox_messages");
        jdbcTemplate.update("DELETE FROM ledger_entries");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM accounts");

        accountId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                        INSERT INTO accounts (
                            id, account_number, balance, currency, status
                        )
                        VALUES (?, ?, ?, 'TRY', 'ACTIVE')
                        """,
                accountId,
                "LEDGER-API-" + accountId,
                new BigDecimal("1000.00"));

        insertLedger(
                Instant.parse("2026-10-20T00:00:00Z"),
                LedgerEntryIds.first());

        insertLedger(
                Instant.parse("2026-10-20T12:00:00Z"),
                LedgerEntryIds.second());

        insertLedger(
                Instant.parse("2026-10-21T00:00:00Z"),
                LedgerEntryIds.outsideRange());
    }

    @Test
    void shouldReturnLedgerEntriesInRangeWithStablePagination() {
        JsonNode firstPage = getLedger(0, 1);

        assertThat(firstPage).hasSize(1);
        assertThat(firstPage.get(0).path("correlationId").asText())
                .isEqualTo(LedgerEntryIds.first().toString());
        assertThat(firstPage.get(0).path("entryType").asText())
                .isEqualTo("DEBIT");
        assertThat(firstPage.get(0).path("status").asText())
                .isEqualTo("COMPLETED");

        JsonNode secondPage = getLedger(1, 1);

        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).path("correlationId").asText())
                .isEqualTo(LedgerEntryIds.second().toString());

        JsonNode thirdPage = getLedger(2, 1);

        assertThat(thirdPage).isEmpty();
    }

    private JsonNode getLedger(int page, int size) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl("http://localhost:" + port + "/internal/ledger")
                .queryParam("from", from.toString())
                .queryParam("to", to.toString())
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .encode()
                .toUri();

        return restTemplate.getForObject(uri, JsonNode.class);
    }

    private void insertLedger(Instant createdAt, UUID correlationId) {
        UUID transferId = UUID.randomUUID();

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
                transferId,
                correlationId,
                accountId,
                java.sql.Timestamp.from(createdAt));
    }

    private static final class LedgerEntryIds {
        private static final UUID FIRST = UUID.randomUUID();
        private static final UUID SECOND = UUID.randomUUID();
        private static final UUID OUTSIDE_RANGE = UUID.randomUUID();

        static UUID first() {
            return FIRST;
        }

        static UUID second() {
            return SECOND;
        }

        static UUID outsideRange() {
            return OUTSIDE_RANGE;
        }
    }
}