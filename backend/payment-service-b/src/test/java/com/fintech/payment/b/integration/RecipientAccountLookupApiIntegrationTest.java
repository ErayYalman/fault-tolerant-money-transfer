package com.fintech.payment.b.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
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
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PaymentBPostgresTestResource.class)
class RecipientAccountLookupApiIntegrationTest {

    private static final UUID RECIPIENT_ACCOUNT_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String RECIPIENT_ACCOUNT_NUMBER = "BANK-B-RECIPIENT-001";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @TestHTTPResource("/internal/recipient-accounts")
    URL recipientAccountsEndpoint;

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            statement.executeUpdate("DELETE FROM outbox_events");
            statement.executeUpdate("DELETE FROM inbox_messages");
            statement.executeUpdate("DELETE FROM ledger_entries");
            statement.executeUpdate("DELETE FROM accounts");
        }

        insertAccount();
    }

    @Test
    @TestSecurity(user = "transfer-service-client", roles = { "recipient-lookup" })
    void shouldReturnRecipientAccountForAuthorizedTransferService()
            throws Exception {

        HttpResponse<String> response = getAccount(RECIPIENT_ACCOUNT_NUMBER);

        assertEquals(
                200,
                response.statusCode(),
                "Unexpected API response: " + response.body());

        JsonNode body = objectMapper.readTree(response.body());

        assertEquals(
                RECIPIENT_ACCOUNT_ID.toString(),
                body.path("accountId").asText());
        assertEquals(
                RECIPIENT_ACCOUNT_NUMBER,
                body.path("accountNumber").asText());
        assertEquals("TRY", body.path("currency").asText());
        assertEquals("ACTIVE", body.path("status").asText());

        assertFalse(body.has("balance"));
    }

    @Test
    @TestSecurity(user = "transfer-service-client", roles = { "recipient-lookup" })
    void shouldReturn404WhenRecipientAccountDoesNotExist()
            throws Exception {

        HttpResponse<String> response = getAccount("BANK-B-MISSING-001");

        assertEquals(404, response.statusCode());
    }

    @Test
    void shouldRejectRequestWithoutAuthentication() throws Exception {
        HttpResponse<String> response = getAccount(RECIPIENT_ACCOUNT_NUMBER);

        assertEquals(401, response.statusCode());
    }

    @Test
    @TestSecurity(user = "customer", roles = { "CUSTOMER" })
    void shouldRejectCustomerFromRecipientLookup() throws Exception {
        HttpResponse<String> response = getAccount(RECIPIENT_ACCOUNT_NUMBER);

        assertEquals(403, response.statusCode());
    }

    @Test
    @TestSecurity(user = "reconciliation-service", roles = { "reconciliation-read" })
    void shouldRejectReconciliationRoleFromRecipientLookup()
            throws Exception {

        HttpResponse<String> response = getAccount(RECIPIENT_ACCOUNT_NUMBER);

        assertEquals(403, response.statusCode());
    }

    @Test
    @TestSecurity(user = "transfer-service-client", roles = { "recipient-lookup" })
    void shouldRejectAccountNumberLongerThan50Characters()
            throws Exception {

        HttpResponse<String> response = getAccount("A".repeat(51));

        assertEquals(400, response.statusCode());
    }

    private HttpResponse<String> getAccount(String accountNumber)
            throws Exception {

        URI uri = URI.create(
                recipientAccountsEndpoint.toString() + "/" + accountNumber);

        return HTTP_CLIENT.send(
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
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
                                    status,
                                    version,
                                    updated_at
                                )
                                VALUES (?, ?, ?, 'TRY', 'ACTIVE', 0, NOW())
                                """)) {

            statement.setObject(1, RECIPIENT_ACCOUNT_ID);
            statement.setString(2, RECIPIENT_ACCOUNT_NUMBER);
            statement.setBigDecimal(3, new BigDecimal("1000.00"));

            statement.executeUpdate();
        }
    }
}
