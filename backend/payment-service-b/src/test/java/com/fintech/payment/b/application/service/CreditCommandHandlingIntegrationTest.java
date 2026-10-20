package com.fintech.payment.b.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fintech.payment.b.application.exception.IdempotencyConflictException;
import com.fintech.payment.b.application.messaging.command.CreditRequestedCommand;
import com.fintech.payment.b.support.PaymentBPostgresTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PaymentBPostgresTestResource.class)
class CreditCommandHandlingIntegrationTest {

    private static final Instant INITIAL_AT = Instant.parse("2026-10-20T09:00:00Z");

    @Inject
    CreditCommandHandlingService creditHandlingService;

    @Inject
    DataSource dataSource;

    @BeforeEach
    void cleanDatabase() throws SQLException {
        dropFailureTrigger();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            statement.executeUpdate("DELETE FROM outbox_events");
            statement.executeUpdate("DELETE FROM inbox_messages");
            statement.executeUpdate("DELETE FROM ledger_entries");
            statement.executeUpdate("DELETE FROM accounts");
        }
    }

    @AfterEach
    void cleanupFailureTrigger() throws SQLException {
        dropFailureTrigger();
    }

    @Test
    void shouldCreditAccountAndPersistLedgerInboxAndOutbox()
            throws SQLException {

        UUID accountId = createAccount(
                new BigDecimal("75.00"),
                "ACTIVE");

        CreditRequestedCommand command = command(
                accountId,
                new BigDecimal("25.00"));

        assertEquals(
                CreditCommandHandlingService.Result.PROCESSED,
                creditHandlingService.handle(command));

        assertMoney("100.00", readBalance(accountId));
        assertEquals(1L, readVersion(accountId));
        assertEquals(1L, countLedger());
        assertEquals(1L, countInbox());
        assertEquals(
                1L,
                countOutbox("credit-completed", "PENDING"));

        assertMoney(
                "75.00",
                ledgerAmount("balance_before"));
        assertMoney(
                "100.00",
                ledgerAmount("balance_after"));

        String ledgerEntryId = readLedgerEntryId();

        assertNotNull(ledgerEntryId);
        assertEquals(
                ledgerEntryId,
                readOutboxLedgerEntryId());

        assertEquals(
                command.correlationId().toString(),
                readOutboxCorrelationId());
    }

    @Test
    void shouldNotCreditAccountTwiceForDuplicateLogicalOperation()
            throws SQLException {

        UUID accountId = createAccount(
                new BigDecimal("75.00"),
                "ACTIVE");

        CreditRequestedCommand first = command(
                accountId,
                new BigDecimal("25.00"));

        CreditRequestedCommand duplicate = new CreditRequestedCommand(
                UUID.randomUUID(),
                first.correlationId(),
                first.transferId(),
                "credit-retry-" + UUID.randomUUID(),
                first.accountId(),
                first.amount(),
                first.currency());

        assertEquals(
                CreditCommandHandlingService.Result.PROCESSED,
                creditHandlingService.handle(first));

        assertEquals(
                CreditCommandHandlingService.Result.DUPLICATE,
                creditHandlingService.handle(duplicate));

        assertMoney("100.00", readBalance(accountId));
        assertEquals(1L, countLedger());
        assertEquals(1L, countInbox());
        assertEquals(
                1L,
                countOutbox("credit-completed", "PENDING"));
    }

    @Test
    void shouldRejectSameLogicalOperationWithDifferentAmount()
            throws SQLException {

        UUID accountId = createAccount(
                new BigDecimal("75.00"),
                "ACTIVE");

        CreditRequestedCommand first = command(
                accountId,
                new BigDecimal("25.00"));

        CreditRequestedCommand conflicting = new CreditRequestedCommand(
                UUID.randomUUID(),
                first.correlationId(),
                first.transferId(),
                "credit-conflict-" + UUID.randomUUID(),
                first.accountId(),
                new BigDecimal("30.00"),
                first.currency());

        assertEquals(
                CreditCommandHandlingService.Result.PROCESSED,
                creditHandlingService.handle(first));

        assertThrows(
                IdempotencyConflictException.class,
                () -> creditHandlingService.handle(conflicting));

        // Çakışan komut, ikinci kredi veya ek kayıt oluşturmamalı.
        assertMoney("100.00", readBalance(accountId));
        assertEquals(1L, countLedger());
        assertEquals(1L, countInbox());
        assertEquals(
                1L,
                countOutbox("credit-completed", "PENDING"));
    }

    @Test
    void shouldPublishFailureWhenAccountIsFrozen()
            throws SQLException {

        UUID accountId = createAccount(
                new BigDecimal("75.00"),
                "FROZEN");

        CreditRequestedCommand command = command(
                accountId,
                new BigDecimal("25.00"));

        assertEquals(
                CreditCommandHandlingService.Result.BUSINESS_FAILURE,
                creditHandlingService.handle(command));

        assertMoney("75.00", readBalance(accountId));
        assertEquals(0L, readVersion(accountId));
        assertEquals(0L, countLedger());
        assertEquals(1L, countInbox());
        assertEquals(
                1L,
                countOutbox("credit-failed", "PENDING"));
        assertEquals(
                "ACCOUNT_NOT_ACTIVE",
                readFailureReason());
    }

    @Test
    void shouldPublishFailureWhenAccountDoesNotExist()
            throws SQLException {

        CreditRequestedCommand command = command(
                UUID.randomUUID(),
                new BigDecimal("25.00"));

        assertEquals(
                CreditCommandHandlingService.Result.BUSINESS_FAILURE,
                creditHandlingService.handle(command));

        assertEquals(0L, countLedger());
        assertEquals(1L, countInbox());
        assertEquals(
                1L,
                countOutbox("credit-failed", "PENDING"));
        assertEquals(
                "ACCOUNT_NOT_FOUND",
                readFailureReason());
    }

    @Test
    void shouldRollbackCreditWhenOutboxInsertFails()
            throws SQLException {

        UUID accountId = createAccount(
                new BigDecimal("75.00"),
                "ACTIVE");

        CreditRequestedCommand command = command(
                accountId,
                new BigDecimal("25.00"));

        installFailureTrigger();

        assertThrows(
                RuntimeException.class,
                () -> creditHandlingService.handle(command));

        // Outbox yazımı başarısızsa credit'in tamamı geri alınmalı.
        assertMoney("75.00", readBalance(accountId));
        assertEquals(0L, readVersion(accountId));
        assertEquals(0L, countLedger());
        assertEquals(0L, countInbox());
        assertEquals(0L, countOutbox("credit-completed", "PENDING"));
    }

    private CreditRequestedCommand command(
            UUID accountId,
            BigDecimal amount) {
        return new CreditRequestedCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "credit-" + UUID.randomUUID(),
                accountId,
                amount,
                "TRY");
    }

    private UUID createAccount(
            BigDecimal balance,
            String status) throws SQLException {

        UUID accountId = UUID.randomUUID();

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
                                VALUES (?, ?, ?, 'TRY', ?, 0, ?)
                                """)) {

            statement.setObject(1, accountId);
            statement.setString(2, "E2E-" + accountId);
            statement.setBigDecimal(3, balance);
            statement.setString(4, status);
            statement.setTimestamp(5, Timestamp.from(INITIAL_AT));

            statement.executeUpdate();
        }

        return accountId;
    }

    private BigDecimal readBalance(UUID accountId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT balance FROM accounts WHERE id = ?")) {

            statement.setObject(1, accountId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError("Account not found");
                }

                return rs.getBigDecimal(1);
            }
        }
    }

    private long readVersion(UUID accountId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT version FROM accounts WHERE id = ?")) {

            statement.setObject(1, accountId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError("Account not found");
                }

                return rs.getLong(1);
            }
        }
    }

    private long countLedger() throws SQLException {
        return count("SELECT COUNT(*) FROM ledger_entries");
    }

    private long countInbox() throws SQLException {
        return count("SELECT COUNT(*) FROM inbox_messages");
    }

    private long countOutbox(String eventType, String status)
            throws SQLException {

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                                SELECT COUNT(*)
                                FROM outbox_events
                                WHERE event_type = ?
                                  AND status = ?
                                """)) {

            statement.setString(1, eventType);
            statement.setString(2, status);

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long count(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {

            rs.next();
            return rs.getLong(1);
        }
    }

    private BigDecimal ledgerAmount(String columnName) throws SQLException {
        if (!"balance_before".equals(columnName)
                && !"balance_after".equals(columnName)) {
            throw new IllegalArgumentException("Unsupported ledger column");
        }

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT " + columnName + " FROM ledger_entries")) {

            if (!rs.next()) {
                throw new AssertionError("Ledger entry not found");
            }

            return rs.getBigDecimal(1);
        }
    }

    private String readLedgerEntryId() throws SQLException {
        return queryString(
                "SELECT id::text FROM ledger_entries");
    }

    private String readOutboxLedgerEntryId() throws SQLException {
        return queryString(
                """
                        SELECT payload ->> 'ledgerEntryId'
                        FROM outbox_events
                        WHERE event_type = 'credit-completed'
                        """);
    }

    private String readOutboxCorrelationId() throws SQLException {
        return queryString(
                """
                        SELECT payload ->> 'correlationId'
                        FROM outbox_events
                        WHERE event_type = 'credit-completed'
                        """);
    }

    private String readFailureReason() throws SQLException {
        return queryString(
                """
                        SELECT payload ->> 'failureReason'
                        FROM outbox_events
                        WHERE event_type = 'credit-failed'
                        """);
    }

    private String queryString(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {

            if (!rs.next()) {
                throw new AssertionError("Expected query result not found");
            }

            return rs.getString(1);
        }
    }

    private void installFailureTrigger() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            statement.execute("""
                    CREATE OR REPLACE FUNCTION reject_credit_completed_outbox()
                    RETURNS TRIGGER AS $$
                    BEGIN
                        IF NEW.event_type = 'credit-completed' THEN
                            RAISE EXCEPTION 'Forced credit Outbox failure';
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);

            statement.execute("""
                    CREATE TRIGGER trg_reject_credit_completed_outbox
                    BEFORE INSERT ON outbox_events
                    FOR EACH ROW
                    EXECUTE FUNCTION reject_credit_completed_outbox()
                    """);
        }
    }

    private void dropFailureTrigger() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            statement.execute("""
                    DROP TRIGGER IF EXISTS trg_reject_credit_completed_outbox
                    ON outbox_events
                    """);

            statement.execute("""
                    DROP FUNCTION IF EXISTS reject_credit_completed_outbox()
                    """);
        }
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual),
                "Expected monetary amount: " + expected);
    }
}