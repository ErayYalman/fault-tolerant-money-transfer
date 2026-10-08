package com.fintech.transfer.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.model.TransferStatus;
import com.fintech.transfer.infrastructure.persistence.adapter.TransferPersistenceAdapter;
import com.fintech.transfer.infrastructure.persistence.repository.TransferJpaRepository;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransferPersistenceAdapterIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transfer_test_db")
            .withUsername("transfer_test_user")
            .withPassword("transfer_test_password");

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
    private TransferPersistenceAdapter persistenceAdapter;

    @Autowired
    private TransferJpaRepository jpaRepository;

    @BeforeEach
    void cleanDatabase() {
        jpaRepository.deleteAll();
    }

    @Test
    void shouldSaveAndLoadTransfer() {
        UUID transferId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();

        Transfer transfer = Transfer.create(
                transferId,
                correlationId,
                "repository-test-key",
                fromAccountId,
                toAccountId,
                new Money(
                        new BigDecimal("2500.00"),
                        "TRY"),
                java.time.Instant.now());

        Transfer saved = persistenceAdapter.save(transfer);

        assertThat(saved.getId())
                .isEqualTo(transferId);

        Transfer loaded = persistenceAdapter.findById(transferId)
                .orElseThrow();

        assertThat(loaded.getId())
                .isEqualTo(transferId);

        assertThat(loaded.getCorrelationId())
                .isEqualTo(correlationId);

        assertThat(loaded.getIdempotencyKey())
                .isEqualTo("repository-test-key");

        assertThat(loaded.getMoney().amount())
                .isEqualByComparingTo("2500.00");

        assertThat(loaded.getMoney().currency())
                .isEqualTo("TRY");

        assertThat(loaded.getStatus())
                .isEqualTo(TransferStatus.PENDING);
    }

    @Test
    void shouldFindTransferByIdempotencyKey() {
        Transfer transfer = Transfer.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "lookup-key",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Money(
                        new BigDecimal("100.00"),
                        "TRY"),
                java.time.Instant.now());

        persistenceAdapter.save(transfer);

        assertThat(
                persistenceAdapter
                        .findByIdempotencyKey("lookup-key"))
                .isPresent()
                .get()
                .extracting(Transfer::getId)
                .isEqualTo(transfer.getId());
    }
}