package com.fintech.payment.a.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fintech.payment.a.application.exception.CompensationInvariantViolationException;
import com.fintech.payment.a.application.messaging.command.DebitCompensationCommand;
import com.fintech.payment.a.application.messaging.command.DebitRequestedCommand;

@Testcontainers
@SpringBootTest(properties = {
                "app.kafka.enabled=false",
                "app.outbox.relay.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DebitCompensationHandlingIntegrationTest {

        @SuppressWarnings("resource")
        @Container
        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("payment_a_compensation_test_db")
                        .withUsername("payment_a_test_user")
                        .withPassword("payment_a_test_password");

        @DynamicPropertySource
        static void registerProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
                registry.add("spring.datasource.username", POSTGRES::getUsername);
                registry.add("spring.datasource.password", POSTGRES::getPassword);
        }

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private DebitCommandHandlingService debitHandlingService;

        @Autowired
        private DebitCompensationHandlingService compensationHandlingService;

        @BeforeEach
        void cleanDatabase() {
                dropFailureTrigger();

                jdbcTemplate.update("DELETE FROM inbox_messages");
                jdbcTemplate.update("DELETE FROM ledger_entries");
                jdbcTemplate.update("DELETE FROM outbox_events");
                jdbcTemplate.update("DELETE FROM accounts");
        }

        @Test
        void shouldReverseSuccessfulDebitAndPublishCompensatedEvent() {
                UUID accountId = createAccount("100.00");
                DebitRequestedCommand debitCommand = debitCommand(
                                accountId,
                                new BigDecimal("25.00"));

                assertThat(debitHandlingService.handle(debitCommand))
                                .isEqualTo(DebitCommandHandlingService.Result.PROCESSED);

                assertThat(balance(accountId))
                                .isEqualByComparingTo("75.00");

                DebitCompensationCommand compensationCommand = compensationCommand(debitCommand);

                assertThat(compensationHandlingService.handle(compensationCommand))
                                .isEqualTo(
                                                DebitCompensationHandlingService.Result.PROCESSED);

                assertThat(balance(accountId))
                                .isEqualByComparingTo("100.00");

                assertThat(count("ledger_entries")).isEqualTo(2L);
                assertThat(count("inbox_messages")).isEqualTo(2L);

                assertThat(countOutbox("debit-completed")).isEqualTo(1L);
                assertThat(countOutbox("debit-compensated")).isEqualTo(1L);

                assertThat(ledgerBalance("REVERSAL", "balance_before"))
                                .isEqualByComparingTo("75.00");

                assertThat(ledgerBalance("REVERSAL", "balance_after"))
                                .isEqualByComparingTo("100.00");

                String reversalLedgerEntryId = jdbcTemplate.queryForObject(
                                """
                                                SELECT payload ->> 'reversalLedgerEntryId'
                                                FROM outbox_events
                                                WHERE event_type = 'debit-compensated'
                                                """,
                                String.class);

                assertThat(reversalLedgerEntryId).isNotBlank();

                assertThat(jdbcTemplate.queryForObject(
                                """
                                                SELECT id::text
                                                FROM ledger_entries
                                                WHERE entry_type = 'REVERSAL'
                                                """,
                                String.class)).isEqualTo(reversalLedgerEntryId);
        }

        @Test
        void shouldNotCreateSecondReversalForDuplicateCompensation() {
                UUID accountId = createAccount("100.00");
                DebitRequestedCommand debitCommand = debitCommand(
                                accountId,
                                new BigDecimal("25.00"));

                debitHandlingService.handle(debitCommand);

                DebitCompensationCommand first = compensationCommand(debitCommand);

                DebitCompensationCommand duplicate = new DebitCompensationCommand(
                                UUID.randomUUID(),
                                first.correlationId(),
                                first.transferId(),
                                "compensation-retry-" + UUID.randomUUID(),
                                first.accountId(),
                                first.amount(),
                                first.currency(),
                                first.reason());

                assertThat(compensationHandlingService.handle(first))
                                .isEqualTo(
                                                DebitCompensationHandlingService.Result.PROCESSED);

                assertThat(compensationHandlingService.handle(duplicate))
                                .isEqualTo(
                                                DebitCompensationHandlingService.Result.DUPLICATE);

                assertThat(balance(accountId))
                                .isEqualByComparingTo("100.00");

                assertThat(count("ledger_entries")).isEqualTo(2L);
                assertThat(countInboxCommand("debit-compensate")).isEqualTo(1L);
                assertThat(countOutbox("debit-compensated")).isEqualTo(1L);
        }

        @Test
        void shouldRejectCompensationThatDoesNotMatchOriginalDebit() {
                UUID accountId = createAccount("100.00");
                DebitRequestedCommand debitCommand = debitCommand(
                                accountId,
                                new BigDecimal("25.00"));

                debitHandlingService.handle(debitCommand);

                DebitCompensationCommand invalidCommand = new DebitCompensationCommand(
                                UUID.randomUUID(),
                                debitCommand.correlationId(),
                                debitCommand.transferId(),
                                "compensation-invalid-" + UUID.randomUUID(),
                                accountId,
                                new BigDecimal("30.00"),
                                debitCommand.currency(),
                                "CREDIT_FAILED");

                assertThatThrownBy(
                                () -> compensationHandlingService.handle(invalidCommand))
                                .isInstanceOf(CompensationInvariantViolationException.class);

                // Orijinal debit durur; hatalı compensation hiçbir etki yaratmaz.
                assertThat(balance(accountId))
                                .isEqualByComparingTo("75.00");

                assertThat(count("ledger_entries")).isEqualTo(1L);
                assertThat(countInboxCommand("debit-compensate")).isZero();
                assertThat(countOutbox("debit-compensated")).isZero();
        }

        @Test
        void shouldRejectCompensationWhenOriginalDebitDoesNotExist() {
                UUID accountId = createAccount("100.00");

                DebitCompensationCommand command = new DebitCompensationCommand(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "compensation-without-debit-" + UUID.randomUUID(),
                                accountId,
                                new BigDecimal("25.00"),
                                "TRY",
                                "CREDIT_FAILED");

                assertThatThrownBy(
                                () -> compensationHandlingService.handle(command))
                                .isInstanceOf(CompensationInvariantViolationException.class);

                assertThat(balance(accountId))
                                .isEqualByComparingTo("100.00");

                assertThat(count("inbox_messages")).isZero();
                assertThat(count("ledger_entries")).isZero();
                assertThat(count("outbox_events")).isZero();
        }

        @Test
        void shouldRollbackReversalWhenOutboxInsertFails() {
                UUID accountId = createAccount("100.00");
                DebitRequestedCommand debitCommand = debitCommand(
                                accountId,
                                new BigDecimal("25.00"));

                debitHandlingService.handle(debitCommand);

                installFailureTrigger();

                try {
                        assertThatThrownBy(
                                        () -> compensationHandlingService.handle(
                                                        compensationCommand(debitCommand)))
                                        .isInstanceOf(RuntimeException.class);
                } finally {
                        dropFailureTrigger();
                }

                // Debit daha önce commit edilmişti; başarısız reversal geri alınmalı.
                assertThat(balance(accountId))
                                .isEqualByComparingTo("75.00");

                assertThat(count("ledger_entries")).isEqualTo(1L);
                assertThat(countInboxCommand("debit-requested")).isEqualTo(1L);
                assertThat(countInboxCommand("debit-compensate")).isZero();

                assertThat(countOutbox("debit-completed")).isEqualTo(1L);
                assertThat(countOutbox("debit-compensated")).isZero();
        }

        private UUID createAccount(String initialBalance) {
                UUID accountId = UUID.randomUUID();

                jdbcTemplate.update(
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
                                                """,
                                accountId,
                                "TEST-" + UUID.randomUUID(),
                                new BigDecimal(initialBalance));

                return accountId;
        }

        private DebitRequestedCommand debitCommand(
                        UUID accountId,
                        BigDecimal amount) {
                return new DebitRequestedCommand(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "debit-" + UUID.randomUUID(),
                                accountId,
                                amount,
                                "TRY");
        }

        private DebitCompensationCommand compensationCommand(
                        DebitRequestedCommand debit) {
                return new DebitCompensationCommand(
                                UUID.randomUUID(),
                                debit.correlationId(),
                                debit.transferId(),
                                "compensation-" + UUID.randomUUID(),
                                debit.accountId(),
                                debit.amount(),
                                debit.currency(),
                                "CREDIT_FAILED");
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

        private long countInboxCommand(String commandType) {
                return jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM inbox_messages WHERE command_type = ?",
                                Long.class,
                                commandType);
        }

        private long countOutbox(String eventType) {
                return jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM outbox_events WHERE event_type = ?",
                                Long.class,
                                eventType);
        }

        private BigDecimal ledgerBalance(
                        String entryType,
                        String columnName) {
                if (!"balance_before".equals(columnName)
                                && !"balance_after".equals(columnName)) {
                        throw new IllegalArgumentException(
                                        "Unsupported ledger balance column");
                }

                return jdbcTemplate.queryForObject(
                                "SELECT " + columnName +
                                                " FROM ledger_entries WHERE entry_type = ?",
                                BigDecimal.class,
                                entryType);
        }

        private void installFailureTrigger() {
                jdbcTemplate.execute("""
                                CREATE OR REPLACE FUNCTION reject_debit_compensated_outbox()
                                RETURNS TRIGGER AS $$
                                BEGIN
                                    IF NEW.event_type = 'debit-compensated' THEN
                                        RAISE EXCEPTION 'Forced compensation Outbox failure';
                                    END IF;
                                    RETURN NEW;
                                END;
                                $$ LANGUAGE plpgsql
                                """);

                jdbcTemplate.execute("""
                                CREATE TRIGGER trg_reject_debit_compensated_outbox
                                BEFORE INSERT ON outbox_events
                                FOR EACH ROW
                                EXECUTE FUNCTION reject_debit_compensated_outbox()
                                """);
        }

        private void dropFailureTrigger() {
                jdbcTemplate.execute("""
                                DROP TRIGGER IF EXISTS trg_reject_debit_compensated_outbox
                                ON outbox_events
                                """);

                jdbcTemplate.execute("""
                                DROP FUNCTION IF EXISTS reject_debit_compensated_outbox()
                                """);
        }
}