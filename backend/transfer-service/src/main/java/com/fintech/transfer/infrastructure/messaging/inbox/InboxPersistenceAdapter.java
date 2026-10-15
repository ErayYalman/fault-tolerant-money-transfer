package com.fintech.transfer.infrastructure.messaging.inbox;

import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.transfer.application.port.out.ConsumedEventInbox;

@Repository
public class InboxPersistenceAdapter implements ConsumedEventInbox {

    private static final String INSERT_IF_ABSENT_SQL = """
            INSERT INTO inbox_messages (
                event_id,
                event_type,
                correlation_id
            )
            VALUES (?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public InboxPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryRegister(
            UUID eventId,
            String eventType,
            UUID correlationId
    ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");

        if (eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "eventType must not be blank"
            );
        }

        int insertedRows = jdbcTemplate.update(
                INSERT_IF_ABSENT_SQL,
                eventId,
                eventType,
                correlationId
        );

        return insertedRows == 1;
    }
}