package com.fintech.transfer.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fintech.transfer.domain.model.Money;
import com.fintech.transfer.domain.model.SagaState;
import com.fintech.transfer.domain.model.SagaStep;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.out.SagaStateRepository;
import com.fintech.transfer.infrastructure.persistence.adapter.TransferPersistenceAdapter;
import com.fintech.transfer.infrastructure.persistence.repository.SagaStateJpaRepository;
import com.fintech.transfer.infrastructure.persistence.repository.TransferJpaRepository;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SagaStatePersistenceAdapterIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transfer_saga_test_db")
            .withUsername("transfer_saga_test_user")
            .withPassword("transfer_saga_test_password");

    @DynamicPropertySource
    static void registerDataSourceProperties(
            DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl);

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername);

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword);
    }

    @Autowired
    private SagaStateRepository sagaStateRepository;

    @Autowired
    private SagaStateJpaRepository sagaStateJpaRepository;

    @Autowired
    private TransferPersistenceAdapter transferPersistenceAdapter;

    @Autowired
    private TransferJpaRepository transferJpaRepository;

    @BeforeEach
    void cleanDatabase() {
        sagaStateJpaRepository.deleteAll();
        transferJpaRepository.deleteAll();
    }

    @Test
    void shouldSaveAndLoadSagaState() {
        UUID transferId = UUID.randomUUID();

        Transfer transfer = Transfer.create(
                transferId,
                UUID.randomUUID(),
                "saga-test-idempotency-key",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Money(
                        new BigDecimal("100.00"),
                        "TRY"),
                Instant.now());

        transferPersistenceAdapter.save(transfer);

        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                transferId,
                Instant.now());

        sagaState.putPayload(
                "test",
                "value",
                Instant.now());

        sagaStateRepository.save(sagaState);

        SagaState loaded = sagaStateRepository
                .findByTransferId(transferId)
                .orElseThrow();

        assertThat(loaded.getTransferId())
                .isEqualTo(transferId);

        assertThat(loaded.getCurrentStep())
                .isEqualTo(SagaStep.DEBIT);

        assertThat(loaded.getPayload())
                .containsEntry("test", "value");
    }
}