package com.fintech.payment.b.integration.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.payment.b.application.messaging.command.CreditRequestedCommand;
import com.fintech.payment.b.application.outbox.OutboxRelay;
import com.fintech.payment.b.infrastructure.messaging.kafka.PaymentBKafkaTopics;
import com.fintech.payment.b.support.PaymentBKafkaTestResource;
import com.fintech.payment.b.support.PaymentBPostgresTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PaymentBPostgresTestResource.class)
@QuarkusTestResource(PaymentBKafkaTestResource.class)
class PaymentBKafkaEndToEndIntegrationTest {

    private static final Instant INITIAL_AT = Instant.parse("2026-10-20T09:00:00Z");

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    OutboxRelay outboxRelay;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @BeforeEach
    void cleanDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            statement.executeUpdate("DELETE FROM outbox_events");
            statement.executeUpdate("DELETE FROM inbox_messages");
            statement.executeUpdate("DELETE FROM ledger_entries");
            statement.executeUpdate("DELETE FROM accounts");
        }
    }

    @Test
    void shouldConsumeCreditAndPublishCompletionEventThroughOutbox()
            throws Exception {

        UUID accountId = createAccount(
                new BigDecimal("75.00"),
                "ACTIVE");

        CreditRequestedCommand command = command(
                accountId,
                new BigDecimal("25.00"));

        sendCommand(command);

        awaitCondition("successful credit transaction", () -> {
            assertMoney("100.00", balance(accountId));
            assertEquals(1L, count("SELECT COUNT(*) FROM ledger_entries"));
            assertEquals(1L, count("SELECT COUNT(*) FROM inbox_messages"));
            assertEquals(
                    "PENDING",
                    outboxStatus("credit-completed"));
        });

        assertEquals(
                1,
                outboxRelay.publishPendingBatch(10));

        ConsumerRecord<String, String> eventRecord = awaitRecord(
                PaymentBKafkaTopics.CREDIT_COMPLETED,
                command.correlationId());

        JsonNode event = objectMapper.readTree(eventRecord.value());

        assertEquals(command.correlationId().toString(), eventRecord.key());
        assertNotNull(event.path("eventId").asText());
        assertEquals(
                command.correlationId().toString(),
                event.path("correlationId").asText());

        String persistedLedgerEntryId = ledgerEntryId(command.correlationId());

        assertEquals(
                persistedLedgerEntryId,
                event.path("ledgerEntryId").asText());

        awaitCondition("Outbox publication status", () -> assertEquals(
                "PUBLISHED",
                outboxStatus("credit-completed")));

        assertMoney("100.00", balance(accountId));
    }

    @Test
    void shouldConsumeCreditAndPublishFailureForFrozenAccount()
            throws Exception {

        UUID accountId = createAccount(
                new BigDecimal("75.00"),
                "FROZEN");

        CreditRequestedCommand command = command(
                accountId,
                new BigDecimal("25.00"));

        sendCommand(command);

        awaitCondition("frozen account rejection", () -> {
            assertMoney("75.00", balance(accountId));
            assertEquals(0L, count("SELECT COUNT(*) FROM ledger_entries"));
            assertEquals(1L, count("SELECT COUNT(*) FROM inbox_messages"));
            assertEquals(
                    "PENDING",
                    outboxStatus("credit-failed"));

            assertEquals(
                    "ACCOUNT_NOT_ACTIVE",
                    outboxFailureReason());
        });

        assertEquals(
                1,
                outboxRelay.publishPendingBatch(10));

        ConsumerRecord<String, String> eventRecord = awaitRecord(
                PaymentBKafkaTopics.CREDIT_FAILED,
                command.correlationId());

        JsonNode event = objectMapper.readTree(eventRecord.value());

        assertEquals(command.correlationId().toString(), eventRecord.key());
        assertEquals(
                command.correlationId().toString(),
                event.path("correlationId").asText());
        assertEquals(
                "ACCOUNT_NOT_ACTIVE",
                event.path("failureReason").asText());

        awaitCondition("failure Outbox publication status", () -> assertEquals(
                "PUBLISHED",
                outboxStatus("credit-failed")));

        assertMoney("75.00", balance(accountId));
    }

    private CreditRequestedCommand command(
            UUID accountId,
            BigDecimal amount) {
        return new CreditRequestedCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "credit-e2e-" + UUID.randomUUID(),
                accountId,
                amount,
                "TRY");
    }

    private void sendCommand(CreditRequestedCommand command)
            throws Exception {

        Properties properties = new Properties();
        properties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers);
        properties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        properties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {

            producer.send(
                    new ProducerRecord<>(
                            PaymentBKafkaTopics.CREDIT_REQUESTED,
                            command.correlationId().toString(),
                            objectMapper.writeValueAsString(command)))
                    .get(10, TimeUnit.SECONDS);
        }
    }

    private ConsumerRecord<String, String> awaitRecord(
            String topic,
            UUID correlationId) throws Exception {

        Properties properties = new Properties();
        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers);
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "payment-b-e2e-observer-" + UUID.randomUUID());
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false);
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());

        long deadline = System.nanoTime()
                + Duration.ofSeconds(20).toNanos();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {

            consumer.subscribe(List.of(topic));

            while (System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofMillis(250));

                for (ConsumerRecord<String, String> record : records) {
                    JsonNode payload = objectMapper.readTree(record.value());

                    if (correlationId.toString().equals(
                            payload.path("correlationId").asText())) {
                        return record;
                    }
                }
            }
        }

        throw new AssertionError(
                "Timed out waiting for topic "
                        + topic
                        + " and correlationId "
                        + correlationId);
    }

    private UUID createAccount(
            BigDecimal initialBalance,
            String status) throws Exception {

        UUID accountId = UUID.randomUUID();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
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
                                VALUES (?, ?, ?, 'TRY', ?, 0, ?)
                                """)) {

            statement.setObject(1, accountId);
            statement.setString(2, "E2E-" + accountId);
            statement.setBigDecimal(3, initialBalance);
            statement.setString(4, status);
            statement.setTimestamp(5, Timestamp.from(INITIAL_AT));

            statement.executeUpdate();
        }

        return accountId;
    }

    private BigDecimal balance(UUID accountId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT balance FROM accounts WHERE id = ?")) {

            statement.setObject(1, accountId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("Account not found");
                }

                return resultSet.getBigDecimal(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long count(String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {

            resultSet.next();
            return resultSet.getLong(1);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String ledgerEntryId(UUID correlationId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                                SELECT id::text
                                FROM ledger_entries
                                WHERE correlation_id = ?
                                """)) {

            statement.setObject(1, correlationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("Ledger entry not found");
                }

                return resultSet.getString(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String outboxStatus(String eventType) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                                SELECT status
                                FROM outbox_events
                                WHERE event_type = ?
                                """)) {

            statement.setString(1, eventType);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError(
                            "Outbox event not found: " + eventType);
                }

                return resultSet.getString(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String outboxFailureReason() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        """
                                SELECT payload ->> 'failureReason'
                                FROM outbox_events
                                WHERE event_type = 'credit-failed'
                                """)) {

            if (!resultSet.next()) {
                throw new AssertionError("Credit failure event not found");
            }

            return resultSet.getString(1);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void awaitCondition(
            String description,
            Runnable assertion) throws InterruptedException {

        long deadline = System.nanoTime()
                + Duration.ofSeconds(20).toNanos();

        AssertionError lastFailure = null;

        while (System.nanoTime() < deadline) {
            try {
                assertion.run();
                return;
            } catch (AssertionError exception) {
                lastFailure = exception;
            }

            Thread.sleep(100);
        }

        throw new AssertionError(
                "Timed out waiting for: " + description,
                lastFailure);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertTrue(
                new BigDecimal(expected).compareTo(actual) == 0,
                "Expected " + expected + " but received " + actual);
    }
}