package com.fintech.payment.b.infrastructure.messaging.inbox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.sql.DataSource;

import com.fintech.payment.b.application.exception.IdempotencyConflictException;
import com.fintech.payment.b.application.messaging.ConsumedCommand;
import com.fintech.payment.b.application.port.out.ConsumedCommandInbox;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
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

    private final DataSource dataSource;

    public InboxPersistenceAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public boolean tryRegister(ConsumedCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        try (Connection connection = dataSource.getConnection()) {
            int inserted;

            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                statement.setObject(1, command.messageId());
                statement.setString(2, command.idempotencyKey());
                statement.setObject(3, command.transferId());
                statement.setObject(4, command.correlationId());
                statement.setString(5, command.commandType());
                statement.setString(6, command.requestHash());

                inserted = statement.executeUpdate();
            }

            if (inserted == 1) {
                return true;
            }

            List<InboxRecord> matches = new ArrayList<>();

            try (PreparedStatement statement = connection.prepareStatement(FIND_CONFLICT_SQL)) {
                statement.setObject(1, command.messageId());
                statement.setString(2, command.idempotencyKey());
                statement.setObject(3, command.correlationId());
                statement.setString(4, command.commandType());

                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        matches.add(new InboxRecord(
                                resultSet.getObject("message_id", UUID.class),
                                resultSet.getString("idempotency_key"),
                                resultSet.getObject("transfer_id", UUID.class),
                                resultSet.getObject("correlation_id", UUID.class),
                                resultSet.getString("command_type"),
                                resultSet.getString("request_hash")));
                    }
                }
            }

            if (matches.size() != 1) {
                throw new IdempotencyConflictException(
                        "Inbox keys identify conflicting records");
            }

            InboxRecord existing = matches.get(0);

            boolean sameLogicalOperation = existing.transferId().equals(command.transferId())
                    && existing.correlationId().equals(command.correlationId())
                    && existing.commandType().equals(command.commandType());

            if (!sameLogicalOperation) {
                throw new IdempotencyConflictException(
                        "An Inbox key was reused for another operation");
            }

            if (!existing.requestHash().equals(command.requestHash())) {
                throw new IdempotencyConflictException(
                        "The same logical credit operation has different content");
            }

            return false;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not register the consumed credit command",
                    exception);
        }
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