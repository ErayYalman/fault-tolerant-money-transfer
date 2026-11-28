package com.fintech.payment.a.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fintech.payment.a.domain.model.Account;
import com.fintech.payment.a.domain.model.InternalTransfer;
import com.fintech.payment.a.domain.port.out.AccountRepository;
import com.fintech.payment.a.domain.port.out.InternalTransferRepository;

@Testcontainers
@SpringBootTest(properties = {
        "app.kafka.enabled=false",
        "app.outbox.relay.enabled=false"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class InternalTransferPersistenceIntegrationTest {

    private static final String OWNER_SUBJECT = "internal-transfer-owner";

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("payment_a_internal_transfer_test_db")
                    .withUsername("payment_a_internal_transfer_user")
                    .withPassword("payment_a_internal_transfer_password");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final UUID FIRST_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID SECOND_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    InternalTransferRepository internalTransferRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM internal_transfers");
        jdbcTemplate.update("DELETE FROM inbox_messages");
        jdbcTemplate.update("DELETE FROM ledger_entries");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    void shouldLockAccountsInAscendingDatabaseIdOrder() {
        insertAccount(FIRST_ACCOUNT_ID, "LOCK-001");
        insertAccount(SECOND_ACCOUNT_ID, "LOCK-002");

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        List<Account> lockedAccounts = transaction.execute(status ->
                accountRepository.findAllByIdsForUpdate(
                        List.of(SECOND_ACCOUNT_ID, FIRST_ACCOUNT_ID)));

        assertThat(lockedAccounts)
                .extracting(Account::getId)
                .containsExactly(
                        FIRST_ACCOUNT_ID,
                        SECOND_ACCOUNT_ID);
    }

    @Test
    void shouldPersistAndFindInternalTransferByOwnerAndIdempotencyKey() {
        insertAccount(FIRST_ACCOUNT_ID, "PERSIST-001");
        insertAccount(SECOND_ACCOUNT_ID, "PERSIST-002");

        InternalTransfer transfer = createTransfer(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "internal-transfer-persist-001",
                "a".repeat(64));

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status ->
                internalTransferRepository.save(transfer));

        assertThat(
                internalTransferRepository.findByOwnerSubjectAndIdempotencyKey(
                        OWNER_SUBJECT,
                        "internal-transfer-persist-001"))
                .contains(transfer);
    }

    @Test
    void shouldEnforceOwnerScopedIdempotencyKeyAtDatabaseLevel() {
        insertAccount(FIRST_ACCOUNT_ID, "IDEMPOTENCY-001");
        insertAccount(SECOND_ACCOUNT_ID, "IDEMPOTENCY-002");

        InternalTransfer first = createTransfer(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "same-idempotency-key",
                "b".repeat(64));

        InternalTransfer second = createTransfer(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "same-idempotency-key",
                "c".repeat(64));

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status ->
                internalTransferRepository.save(first));

        assertThatThrownBy(() ->
                transaction.executeWithoutResult(status ->
                        internalTransferRepository.save(second)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private InternalTransfer createTransfer(
            UUID transferId,
            UUID correlationId,
            String idempotencyKey,
            String fingerprint) {

        return new InternalTransfer(
                transferId,
                correlationId,
                OWNER_SUBJECT,
                FIRST_ACCOUNT_ID,
                SECOND_ACCOUNT_ID,
                new BigDecimal("25.00"),
                "TRY",
                idempotencyKey,
                fingerprint,
                Instant.parse("2026-11-28T10:00:00Z"));
    }

    private void insertAccount(UUID accountId, String accountNumber) {
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
                VALUES (?, ?, ?, 1000.00, 'TRY', 'ACTIVE')
                """,
                accountId,
                accountNumber,
                OWNER_SUBJECT);
    }
}
