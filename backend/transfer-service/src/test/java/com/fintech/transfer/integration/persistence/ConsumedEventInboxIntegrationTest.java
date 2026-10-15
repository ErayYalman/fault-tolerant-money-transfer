package com.fintech.transfer.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fintech.transfer.application.port.out.ConsumedEventInbox;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class ConsumedEventInboxIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("transfer_inbox_test_db")
                    .withUsername("transfer_inbox_test_user")
                    .withPassword("transfer_inbox_test_password");

    @DynamicPropertySource
    static void registerDataSourceProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ConsumedEventInbox consumedEventInbox;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM inbox_messages");

        transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    @Test
    void shouldRegisterEventOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Boolean firstAttempt = transactionTemplate.execute(
                status -> consumedEventInbox.tryRegister(
                        eventId,
                        "debit-completed",
                        correlationId
                )
        );

        Boolean duplicateAttempt = transactionTemplate.execute(
                status -> consumedEventInbox.tryRegister(
                        eventId,
                        "debit-completed",
                        correlationId
                )
        );

        assertThat(firstAttempt).isTrue();
        assertThat(duplicateAttempt).isFalse();

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_messages WHERE event_id = ?",
                Long.class,
                eventId
        );

        assertThat(rowCount).isEqualTo(1L);
    }

    @Test
    void shouldRollbackInboxRecordWhenTransactionFails() {
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    consumedEventInbox.tryRegister(
                            eventId,
                            "debit-completed",
                            UUID.randomUUID()
                    );

                    throw new IllegalStateException("Force rollback");
                })
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Force rollback");

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_messages WHERE event_id = ?",
                Long.class,
                eventId
        );

        assertThat(rowCount).isZero();
    }
}