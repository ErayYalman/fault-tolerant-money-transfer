
package com.fintech.transfer.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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

import com.fintech.transfer.application.messaging.event.DebitCompletedEvent;
import com.fintech.transfer.application.service.TransferCreationService;
import com.fintech.transfer.application.service.TransferEventHandlingService;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransferEventHandlingIntegrationTest {

    private static final String TRIGGER_NAME = "trg_reject_credit_requested_test";

    private static final String FUNCTION_NAME = "reject_credit_requested_outbox_test";

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transfer_event_test_db")
            .withUsername("transfer_event_test_user")
            .withPassword("transfer_event_test_password");

    @DynamicPropertySource
    static void registerDataSourceProperties(
            DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransferCreationService transferCreationService;

    @Autowired
    private TransferEventHandlingService eventHandlingService;

    @BeforeEach
    void cleanDatabase() {
        dropFailureTrigger();

        jdbcTemplate.update("DELETE FROM inbox_messages");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM saga_state");
        jdbcTemplate.update("DELETE FROM transfers");
    }

    @AfterEach
    void cleanupDatabaseTrigger() {
        dropFailureTrigger();
    }

    @Test
    void shouldUpdateSagaAndWriteCreditCommandAtomically() {
        Transfer transfer = createDebitRequestedTransfer();
        DebitCompletedEvent event = createDebitCompletedEvent(transfer);

        boolean processed = eventHandlingService.handle(event);

        assertThat(processed).isTrue();

        assertThat(statusOf(transfer.getId()))
                .isEqualTo("CREDIT_REQUESTED");

        assertThat(sagaStepOf(transfer.getId()))
                .isEqualTo("CREDIT");

        assertThat(inboxCount(event.eventId()))
                .isEqualTo(1L);

        assertThat(outboxCount(
                transfer.getId(),
                "credit-requested")).isEqualTo(1L);

        String commandAccountId = jdbcTemplate.queryForObject(
                """
                        SELECT payload ->> 'accountId'
                        FROM outbox_events
                        WHERE aggregate_id = ?
                          AND event_type = 'credit-requested'
                        """,
                String.class,
                transfer.getId());

        assertThat(commandAccountId)
                .isEqualTo(transfer.getToAccountId().toString());
    }

    @Test
    void shouldNotCreateSecondCreditCommandForDuplicateEvent() {
        Transfer transfer = createDebitRequestedTransfer();
        DebitCompletedEvent event = createDebitCompletedEvent(transfer);

        assertThat(eventHandlingService.handle(event)).isTrue();
        assertThat(eventHandlingService.handle(event)).isFalse();

        assertThat(statusOf(transfer.getId()))
                .isEqualTo("CREDIT_REQUESTED");

        assertThat(inboxCount(event.eventId()))
                .isEqualTo(1L);

        assertThat(outboxCount(
                transfer.getId(),
                "credit-requested")).isEqualTo(1L);
    }

    @Test
    void shouldRollbackInboxSagaAndOutboxWhenCommandInsertFails() {
        Transfer transfer = createDebitRequestedTransfer();
        DebitCompletedEvent event = createDebitCompletedEvent(transfer);

        installFailureTrigger();

        assertThatThrownBy(() -> eventHandlingService.handle(event))
                .isInstanceOf(RuntimeException.class);

        assertThat(statusOf(transfer.getId()))
                .isEqualTo("DEBIT_REQUESTED");

        assertThat(sagaStepOf(transfer.getId()))
                .isEqualTo("DEBIT");

        assertThat(inboxCount(event.eventId()))
                .isZero();

        assertThat(outboxCount(
                transfer.getId(),
                "credit-requested")).isZero();

        assertThat(outboxCount(
                transfer.getId(),
                "debit-requested")).isEqualTo(1L);
    }

    private Transfer createDebitRequestedTransfer() {
        return transferCreationService.create(
                new InitiateTransferUseCase.InitiateTransferCommand(
                        new BigDecimal("125.00"),
                        "TRY",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "event-integration-" + UUID.randomUUID()));
    }

    private DebitCompletedEvent createDebitCompletedEvent(
            Transfer transfer) {
        return new DebitCompletedEvent(
                UUID.randomUUID(),
                transfer.getCorrelationId(),
                UUID.randomUUID(),
                Instant.now());
    }

    private String statusOf(UUID transferId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM transfers WHERE id = ?",
                String.class,
                transferId);
    }

    private String sagaStepOf(UUID transferId) {
        return jdbcTemplate.queryForObject(
                "SELECT current_step FROM saga_state WHERE transfer_id = ?",
                String.class,
                transferId);
    }

    private Long inboxCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_messages WHERE event_id = ?",
                Long.class,
                eventId);
    }

    private Long outboxCount(UUID transferId, String eventType) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM outbox_events
                        WHERE aggregate_id = ?
                          AND event_type = ?
                        """,
                Long.class,
                transferId,
                eventType);
    }

    private void installFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION reject_credit_requested_outbox_test()
                RETURNS TRIGGER AS $$
                BEGIN
                    IF NEW.event_type = 'credit-requested' THEN
                        RAISE EXCEPTION
                            'Forced Outbox failure for atomicity test';
                    END IF;

                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);

        jdbcTemplate.execute("""
                CREATE TRIGGER trg_reject_credit_requested_test
                BEFORE INSERT ON outbox_events
                FOR EACH ROW
                EXECUTE FUNCTION reject_credit_requested_outbox_test()
                """);
    }

    private void dropFailureTrigger() {
        jdbcTemplate.execute(
                "DROP TRIGGER IF EXISTS trg_reject_credit_requested_test "
                        + "ON outbox_events");

        jdbcTemplate.execute(
                "DROP FUNCTION IF EXISTS "
                        + "reject_credit_requested_outbox_test()");
    }
}
