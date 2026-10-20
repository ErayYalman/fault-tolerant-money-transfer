package com.fintech.payment.b.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fintech.payment.b.domain.model.Account;
import com.fintech.payment.b.domain.model.AccountStatus;
import com.fintech.payment.b.domain.port.out.AccountRepository;
import com.fintech.payment.b.support.PaymentBPostgresTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@QuarkusTest
@QuarkusTestResource(PaymentBPostgresTestResource.class)
class AccountPersistenceIntegrationTest {

    private static final Instant INITIAL_AT = Instant.parse("2026-10-20T09:00:00Z");

    private static final Instant CREDIT_AT = Instant.parse("2026-10-20T09:10:00Z");

    @Inject
    AccountRepository accountRepository;

    @Inject
    DataSource dataSource;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            statement.executeUpdate("DELETE FROM outbox_events");
            statement.executeUpdate("DELETE FROM inbox_messages");
            statement.executeUpdate("DELETE FROM ledger_entries");
            statement.executeUpdate("DELETE FROM accounts");
        }
    }

    @Test
    void shouldReturnEmptyWhenAccountDoesNotExist() {
        Optional<Account> result = accountRepository.findById(UUID.randomUUID());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldMapPersistedAccountToDomainModel() throws SQLException {
        UUID accountId = insertAccount(
                new BigDecimal("75.00"),
                AccountStatus.ACTIVE);

        Account account = accountRepository.findById(accountId)
                .orElseThrow();

        assertEquals(accountId, account.getId());
        assertEquals("E2E-" + accountId, account.getAccountNumber());
        assertMoney("75.00", account.getBalance());
        assertEquals("TRY", account.getCurrency());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(0L, account.getVersion());
        assertEquals(INITIAL_AT, account.getUpdatedAt());
    }

    @Test
    @Transactional
    void shouldPersistCreditAndIncrementVersion() throws SQLException {
        UUID accountId = insertAccount(
                new BigDecimal("75.00"),
                AccountStatus.ACTIVE);

        Account account = accountRepository.findById(accountId)
                .orElseThrow();

        account.credit(
                new BigDecimal("25.00"),
                "TRY",
                CREDIT_AT);

        accountRepository.update(account);

        /*
         * Hibernate normally flushes at transaction completion.
         * Explicit flush lets this test verify the persisted version now.
         */
        entityManager.flush();

        assertMoney("100.00", readBalance(accountId));
        assertEquals(1L, readVersion(accountId));
        assertEquals(CREDIT_AT, readUpdatedAt(accountId));
    }

    private UUID insertAccount(
            BigDecimal balance,
            AccountStatus status) throws SQLException {

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
            statement.setString(4, status.name());
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

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("Account not found");
                }

                return resultSet.getBigDecimal("balance");
            }
        }
    }

    private long readVersion(UUID accountId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT version FROM accounts WHERE id = ?")) {

            statement.setObject(1, accountId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("Account not found");
                }

                return resultSet.getLong("version");
            }
        }
    }

    private Instant readUpdatedAt(UUID accountId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT updated_at FROM accounts WHERE id = ?")) {

            statement.setObject(1, accountId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("Account not found");
                }

                return resultSet.getTimestamp("updated_at").toInstant();
            }
        }
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual),
                "Expected monetary amount: " + expected);
    }
}