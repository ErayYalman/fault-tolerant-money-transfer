package com.fintech.payment.a.application.service;

import com.fintech.payment.a.application.exception.IdempotencyConflictException;
import com.fintech.payment.a.application.messaging.command.DebitRequestedCommand;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "app.kafka.enabled=false",
        "app.outbox.relay.enabled=false"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class DebitCommandHandlingIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("payment_a_test_db")
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
    private DebitCommandHandlingService handlingService;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM inbox_messages");
        jdbcTemplate.update("DELETE FROM ledger_entries");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    void shouldDebitAndPersistLedgerAndOutboxAtomically() {
        UUID accountId = createAccount("100.00");
        DebitRequestedCommand command = command(
                accountId,
                new BigDecimal("25.00")
        );

        DebitCommandHandlingService.Result result =
                handlingService.handle(command);

        assertThat(result)
                .isEqualTo(DebitCommandHandlingService.Result.PROCESSED);

        assertThat(balance(accountId))
                .isEqualByComparingTo("75.00");

        assertThat(count("ledger_entries"))
                .isEqualTo(1);

        assertThat(count("inbox_messages"))
                .isEqualTo(1);

        assertThat(countOutbox("debit-completed"))
                .isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE event_type = 'debit-completed'",
                String.class
        )).isEqualTo("PENDING");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload ->> 'correlationId' " +
                        "FROM outbox_events WHERE event_type = 'debit-completed'",
                String.class
        )).isEqualTo(command.correlationId().toString());
    }

    @Test
    void shouldNotApplySameLogicalDebitTwice() {
        UUID accountId = createAccount("100.00");

        DebitRequestedCommand first = command(
                accountId,
                new BigDecimal("25.00")
        );

        // Aynı mantıksal işlem, farklı Kafka messageId ve idempotencyKey.
        DebitRequestedCommand duplicate = new DebitRequestedCommand(
                UUID.randomUUID(),
                first.correlationId(),
                first.transferId(),
                "retry-key-" + UUID.randomUUID(),
                first.accountId(),
                first.amount(),
                first.currency()
        );

        assertThat(handlingService.handle(first))
                .isEqualTo(DebitCommandHandlingService.Result.PROCESSED);

        assertThat(handlingService.handle(duplicate))
                .isEqualTo(DebitCommandHandlingService.Result.DUPLICATE);

        assertThat(balance(accountId))
                .isEqualByComparingTo("75.00");
        assertThat(count("ledger_entries")).isEqualTo(1);
        assertThat(countOutbox("debit-completed")).isEqualTo(1);
        assertThat(count("inbox_messages")).isEqualTo(1);
    }

    @Test
    void shouldPersistFailureEventWhenBalanceIsInsufficient() {
        UUID accountId = createAccount("10.00");
        DebitRequestedCommand command = command(
                accountId,
                new BigDecimal("20.00")
        );

        assertThat(handlingService.handle(command))
                .isEqualTo(
                        DebitCommandHandlingService.Result.BUSINESS_FAILURE
                );

        assertThat(balance(accountId))
                .isEqualByComparingTo("10.00");

        assertThat(count("ledger_entries")).isZero();
        assertThat(countOutbox("debit-completed")).isZero();
        assertThat(countOutbox("debit-failed")).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload ->> 'failureReason' " +
                        "FROM outbox_events WHERE event_type = 'debit-failed'",
                String.class
        )).isEqualTo("INSUFFICIENT_FUNDS");

        // Başarısız iş sonucu da Inbox'a kaydedildiği için yeniden işlenmez.
        assertThat(handlingService.handle(command))
                .isEqualTo(DebitCommandHandlingService.Result.DUPLICATE);

        assertThat(countOutbox("debit-failed")).isEqualTo(1);
        assertThat(count("ledger_entries")).isZero();
    }

    @Test
    void shouldRejectSameLogicalCommandWithDifferentPayload() {
        UUID accountId = createAccount("100.00");
        DebitRequestedCommand original = command(
                accountId,
                new BigDecimal("25.00")
        );

        DebitRequestedCommand conflicting = new DebitRequestedCommand(
                UUID.randomUUID(),
                original.correlationId(),
                original.transferId(),
                "conflicting-key-" + UUID.randomUUID(),
                original.accountId(),
                new BigDecimal("35.00"),
                original.currency()
        );

        assertThat(handlingService.handle(original))
                .isEqualTo(DebitCommandHandlingService.Result.PROCESSED);

        assertThatThrownBy(() -> handlingService.handle(conflicting))
                .isInstanceOf(IdempotencyConflictException.class);

        assertThat(balance(accountId))
                .isEqualByComparingTo("75.00");
        assertThat(count("ledger_entries")).isEqualTo(1);
        assertThat(countOutbox("debit-completed")).isEqualTo(1);
    }

    @Test
    void shouldRollbackInboxBalanceAndLedgerWhenOutboxInsertFails() {
        UUID accountId = createAccount("100.00");
        DebitRequestedCommand command = command(
                accountId,
                new BigDecimal("25.00")
        );

        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION reject_debit_completed_outbox()
                RETURNS trigger AS $$
                BEGIN
                    IF NEW.event_type = 'debit-completed' THEN
                        RAISE EXCEPTION 'forced Outbox insert failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);

        jdbcTemplate.execute("""
                CREATE TRIGGER trg_reject_debit_completed_outbox
                BEFORE INSERT ON outbox_events
                FOR EACH ROW
                EXECUTE FUNCTION reject_debit_completed_outbox()
                """);

        try {
            assertThatThrownBy(() -> handlingService.handle(command))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute("""
                    DROP TRIGGER IF EXISTS trg_reject_debit_completed_outbox
                    ON outbox_events
                    """);
            jdbcTemplate.execute("""
                    DROP FUNCTION IF EXISTS reject_debit_completed_outbox()
                    """);
        }

        assertThat(balance(accountId))
                .isEqualByComparingTo("100.00");

        assertThat(count("inbox_messages")).isZero();
        assertThat(count("ledger_entries")).isZero();
        assertThat(count("outbox_events")).isZero();
    }

    private UUID createAccount(String initialBalance) {
        UUID accountId = UUID.randomUUID();

        jdbcTemplate.update("""
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
                new BigDecimal(initialBalance)
        );

        return accountId;
    }

    private DebitRequestedCommand command(
            UUID accountId,
            BigDecimal amount
    ) {
        return new DebitRequestedCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "debit-" + UUID.randomUUID(),
                accountId,
                amount,
                "TRY"
        );
    }

    private BigDecimal balance(UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal.class,
                accountId
        );
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Long.class
        );
    }

    private long countOutbox(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type = ?",
                Long.class,
                eventType
        );
    }
}