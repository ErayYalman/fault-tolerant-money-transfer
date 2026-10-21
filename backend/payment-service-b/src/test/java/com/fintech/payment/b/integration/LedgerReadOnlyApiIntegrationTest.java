package com.fintech.payment.b.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.payment.b.support.PaymentBPostgresTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PaymentBPostgresTestResource.class)
class LedgerReadOnlyApiIntegrationTest {

    private static final Instant FROM = Instant.parse("2026-10-20T00:00:00Z");

    private static final Instant TO = Instant.parse("2026-10-21T00:00:00Z");

    private static final Instant MIDDLE = Instant.parse("2026-10-20T12:00:00Z");

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @TestHTTPResource("/internal/ledger")
    URL ledgerEndpoint;

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    private UUID accountId;

    private UUID firstCorrelationId;
    private UUID secondCorrelationId;

    @BeforeEach
    void cleanDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            statement.executeUpdate("DELETE FROM outbox_events");
            statement.executeUpdate("DELETE FROM inbox_messages");
            statement.executeUpdate("DELETE FROM ledger_entries");
            statement.executeUpdate("DELETE FROM accounts");
        }

        accountId = UUID.randomUUID();
        firstCorrelationId = UUID.randomUUID();
        secondCorrelationId = UUID.randomUUID();

        insertAccount();

        // Başlangıç zamanı dahil olmalı.
        insertLedger(FROM, firstCorrelationId);

        // Aralığın içindeki kayıt dönmeli.
        insertLedger(MIDDLE, secondCorrelationId);

        // Bitiş zamanı hariç olmalı.
        insertLedger(TO, UUID.randomUUID());
    }

    @Test
    void shouldReturnLedgerEntriesInRangeWithStablePagination()
            throws Exception {

        JsonNode firstPage = getLedger(0, 1);

        assertEquals(1, firstPage.size());
        assertEquals(
                firstCorrelationId.toString(),
                firstPage.get(0).path("correlationId").asText());
        assertEquals(
                "CREDIT",
                firstPage.get(0).path("entryType").asText());
        assertEquals(
                "COMPLETED",
                firstPage.get(0).path("status").asText());

        JsonNode secondPage = getLedger(1, 1);

        assertEquals(1, secondPage.size());
        assertEquals(
                secondCorrelationId.toString(),
                secondPage.get(0).path("correlationId").asText());

        JsonNode thirdPage = getLedger(2, 1);

        assertTrue(thirdPage.isArray());
        assertEquals(0, thirdPage.size());
    }

    @Test
    void shouldRejectInvalidDateRange() throws Exception {
        URI uri = buildUri(TO, FROM, 0, 10);

        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
    }

    private JsonNode getLedger(int page, int size) throws Exception {
        URI uri = buildUri(FROM, TO, page, size);

        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(
                200,
                response.statusCode(),
                "Unexpected API response: " + response.body());

        return objectMapper.readTree(response.body());
    }

    private URI buildUri(
            Instant from,
            Instant to,
            int page,
            int size) {

        String query = "?from=" + encode(from.toString())
                + "&to=" + encode(to.toString())
                + "&page=" + page
                + "&size=" + size;

        return URI.create(ledgerEndpoint.toString() + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void insertAccount() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                                INSERT INTO accounts (
                                    id,
                                    account_number,
                                    balance,
                                    currency,
                                    status
                                )
                                VALUES (?, ?, ?, 'TRY', 'ACTIVE')
                                """)) {

            statement.setObject(1, accountId);
            statement.setString(2, "LEDGER-API-" + accountId);
            statement.setBigDecimal(3, new BigDecimal("1000.00"));

            statement.executeUpdate();
        }
    }

    private void insertLedger(
            Instant createdAt,
            UUID correlationId) throws SQLException {

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
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
                                    ?, ?, ?, ?, 10.00, 100.00, 110.00,
                                    'CREDIT', 'COMPLETED', ?
                                )
                                """)) {

            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, correlationId);
            statement.setObject(4, accountId);
            statement.setTimestamp(5, Timestamp.from(createdAt));

            statement.executeUpdate();
        }
    }
}