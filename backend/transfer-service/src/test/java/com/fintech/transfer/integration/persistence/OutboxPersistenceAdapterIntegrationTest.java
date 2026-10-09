package com.fintech.transfer.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fintech.transfer.application.messaging.OutboundMessage;
import com.fintech.transfer.application.port.out.DomainEventPublisher;
import com.fintech.transfer.application.port.out.OutboxRelayStore;
import com.fintech.transfer.infrastructure.persistence.entity.OutboxStatus;
import com.fintech.transfer.infrastructure.persistence.repository.OutboxJpaRepository;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxPersistenceAdapterIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transfer_outbox_test_db")
            .withUsername("transfer_outbox_test_user")
            .withPassword("transfer_outbox_test_password");

    @DynamicPropertySource
    static void registerDataSourceProperties(
            DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DomainEventPublisher eventPublisher;

    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Autowired
    private OutboxRelayStore outboxRelayStore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void shouldPersistMessageAsPendingInsideTransaction() {
        UUID transferId = UUID.randomUUID();

        OutboundMessage message = new OutboundMessage(
                "TRANSFER",
                transferId,
                "test-message",
                Map.of(
                        "messageId", UUID.randomUUID().toString(),
                        "correlationId", UUID.randomUUID().toString()));

        transactionTemplate.executeWithoutResult(
                status -> eventPublisher.publish(message));

        assertThat(outboxRepository.findAll())
                .hasSize(1);

        var stored = outboxRepository.findAll().getFirst();

        assertThat(stored.getAggregateType()).isEqualTo("TRANSFER");
        assertThat(stored.getAggregateId()).isEqualTo(transferId);
        assertThat(stored.getEventType()).isEqualTo("test-message");
        assertThat(stored.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(stored.getPublishedAt()).isNull();
        assertThat(stored.getPayload())
                .containsKeys("messageId", "correlationId");
    }

    @Test
    void shouldRollbackOutboxInsertWhenBusinessTransactionFails() {
        OutboundMessage message = new OutboundMessage(
                "TRANSFER",
                UUID.randomUUID(),
                "test-rollback",
                Map.of("messageId", UUID.randomUUID().toString()));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(message);
            throw new IllegalStateException("Force rollback");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Force rollback");

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void shouldMarkMessageAsPublished() {
        OutboundMessage message = new OutboundMessage(
                "TRANSFER",
                UUID.randomUUID(),
                "test-published",
                Map.of("messageId", UUID.randomUUID().toString()));

        transactionTemplate.executeWithoutResult(
                status -> eventPublisher.publish(message));

        var stored = outboxRepository.findAll().getFirst();
        Instant publishTime = Instant.now();

        transactionTemplate.executeWithoutResult(
                status -> outboxRelayStore.markPublished(
                        stored.getId(),
                        publishTime));

        var updated = outboxRepository.findById(stored.getId())
                .orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(updated.getPublishedAt()).isNotNull();
    }
}