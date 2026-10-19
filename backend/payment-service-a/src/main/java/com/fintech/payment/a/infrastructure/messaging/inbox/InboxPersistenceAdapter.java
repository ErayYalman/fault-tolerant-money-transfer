package com.fintech.payment.a.infrastructure.messaging.inbox;

import com.fintech.payment.a.application.exception.IdempotencyConflictException;
import com.fintech.payment.a.application.messaging.ConsumedCommand;
import com.fintech.payment.a.application.port.out.ConsumedCommandInbox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class InboxPersistenceAdapter implements ConsumedCommandInbox {

    private static final String INSERT_SQL = """
            INSERT INTO inbox_messages (
                message_id,
                idempotency_key,
                transfer_id,
                correlation_id,
                command_type,
                request_hash
            )
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String FIND_CONFLICT_SQL = """
            SELECT
                message_id,
                idempotency_key,
                transfer_id,
                correlation_id,
                command_type,
                request_hash
            FROM inbox_messages
            WHERE message_id = ?
               OR idempotency_key = ?
               OR (correlation_id = ? AND command_type = ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public InboxPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryRegister(ConsumedCommand command) {
        Objects.requireNonNull(command);

        int inserted = jdbcTemplate.update(
                INSERT_SQL,
                command.messageId(),
                command.idempotencyKey(),
                command.transferId(),
                command.correlationId(),
                command.commandType(),
                command.requestHash());

        if (inserted == 1) {
            return true;
        }

        List<InboxRecord> matches = jdbcTemplate.query(
                FIND_CONFLICT_SQL,
                (rs, rowNum) -> new InboxRecord(
                        rs.getObject("message_id", UUID.class),
                        rs.getString("idempotency_key"),
                        rs.getObject("transfer_id", UUID.class),
                        rs.getObject("correlation_id", UUID.class),
                        rs.getString("command_type"),
                        rs.getString("request_hash")),
                command.messageId(),
                command.idempotencyKey(),
                command.correlationId(),
                command.commandType());

        if (matches.size() != 1) {
            throw new IdempotencyConflictException();
        }

        InboxRecord existing = matches.get(0);

        boolean sameLogicalOperation = existing.transferId().equals(command.transferId())
                && existing.correlationId().equals(command.correlationId())
                && existing.commandType().equals(command.commandType());

        if (!sameLogicalOperation) {
            throw new IdempotencyConflictException();
        }

        /*
         * A null hash can only exist on a row created before V2 was introduced.
         * For such legacy rows, only an exact message/key repeat is treated as
         * a duplicate because the original request content cannot be verified.
         */
        if (existing.requestHash() == null) {
            if (existing.messageId().equals(command.messageId())
                    && existing.idempotencyKey().equals(command.idempotencyKey())) {
                return false;
            }

            throw new IdempotencyConflictException();
        }

        if (!existing.requestHash().equals(command.requestHash())) {
            throw new IdempotencyConflictException();
        }

        return false;
    }

    private record InboxRecord(
            UUID messageId,
            String idempotencyKey,
            UUID transferId,
            UUID correlationId,
            String commandType,
            String requestHash) {
    }
}