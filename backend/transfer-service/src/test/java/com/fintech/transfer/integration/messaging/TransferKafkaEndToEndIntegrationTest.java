package com.fintech.transfer.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fintech.transfer.application.messaging.event.CreditFailedEvent;
import com.fintech.transfer.application.messaging.event.DebitCompensatedEvent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.transfer.application.messaging.event.DebitCompletedEvent;
import com.fintech.transfer.application.outbox.OutboxRelay;
import com.fintech.transfer.application.service.TransferCreationService;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.infrastructure.messaging.kafka.TransferKafkaTopics;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = {
                "app.kafka.enabled=true",
                "app.outbox.relay.enabled=false",
                "spring.kafka.consumer.group-id=transfer-service-e2e-test",
                "spring.kafka.consumer.auto-offset-reset=earliest"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransferKafkaEndToEndIntegrationTest {

        @SuppressWarnings("resource")
        @Container
        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("transfer_kafka_test_db")
                        .withUsername("transfer_kafka_test_user")
                        .withPassword("transfer_kafka_test_password");

        @SuppressWarnings("resource")
        @Container
        static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
                        DockerImageName.parse("confluentinc/cp-kafka:7.8.1"));

        @DynamicPropertySource
        static void registerProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
                registry.add("spring.datasource.username", POSTGRES::getUsername);
                registry.add("spring.datasource.password", POSTGRES::getPassword);
                registry.add(
                                "spring.kafka.bootstrap-servers",
                                KAFKA::getBootstrapServers);
        }

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private KafkaTemplate<String, String> kafkaTemplate;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private TransferCreationService transferCreationService;

        @Autowired
        private OutboxRelay outboxRelay;

        @BeforeEach
        void cleanDatabase() {
                jdbcTemplate.update("DELETE FROM inbox_messages");
                jdbcTemplate.update("DELETE FROM outbox_events");
                jdbcTemplate.update("DELETE FROM saga_state");
                jdbcTemplate.update("DELETE FROM transfers");
        }

        @Test
        void shouldPublishDebitConsumeCompletionAndPublishCredit()
                        throws Exception {

                // 1. Transfer yaratılır; debit-requested Outbox'a yazılır.
                Transfer transfer = transferCreationService.create(
                                new InitiateTransferUseCase.InitiateTransferCommand(
                                                new BigDecimal("125.00"),
                                                "TRY",
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                "kafka-e2e-" + UUID.randomUUID()));

                assertThat(transfer.getStatus().name())
                                .isEqualTo("DEBIT_REQUESTED");

                // 2. Outbox relay debit komutunu gerçek Kafka'ya gönderir.
                assertThat(outboxRelay.publishPendingBatch(10))
                                .isEqualTo(1);

                ConsumerRecord<String, String> debitRecord = awaitRecord(
                                TransferKafkaTopics.DEBIT_REQUESTED,
                                transfer.getCorrelationId());

                JsonNode debitCommand = objectMapper.readTree(debitRecord.value());

                assertThat(debitRecord.key())
                                .isEqualTo(transfer.getCorrelationId().toString());

                assertThat(debitCommand.path("transferId").asText())
                                .isEqualTo(transfer.getId().toString());

                assertThat(debitCommand.path("accountId").asText())
                                .isEqualTo(transfer.getFromAccountId().toString());

                assertThat(debitCommand.path("messageId").asText())
                                .isNotBlank();

                assertThat(debitCommand.path("idempotencyKey").asText())
                                .isNotBlank();

                assertThat(debitCommand.path("amount").decimalValue())
                                .isEqualByComparingTo("125.00");

                // 3. Bank A'nın debit-completed event'ini taklit et.
                DebitCompletedEvent debitCompletedEvent = new DebitCompletedEvent(
                                UUID.randomUUID(),
                                transfer.getCorrelationId(),
                                UUID.randomUUID(),
                                Instant.now());

                kafkaTemplate.send(
                                TransferKafkaTopics.DEBIT_COMPLETED,
                                transfer.getCorrelationId().toString(),
                                objectMapper.writeValueAsString(debitCompletedEvent)).get(10, TimeUnit.SECONDS);

                // 4. Gerçek Kafka listener'ının event'i işlemesini bekle.
                await()
                                .atMost(Duration.ofSeconds(20))
                                .untilAsserted(() -> {
                                        assertThat(transferStatus(transfer.getId()))
                                                        .isEqualTo("CREDIT_REQUESTED");

                                        assertThat(sagaStep(transfer.getId()))
                                                        .isEqualTo("CREDIT");

                                        assertThat(inboxCount(
                                                        debitCompletedEvent.eventId())).isEqualTo(1L);

                                        assertThat(outboxCount(
                                                        transfer.getId(),
                                                        "credit-requested",
                                                        "PENDING")).isEqualTo(1L);
                                });

                // 5. Outbox relay credit komutunu Kafka'ya gönderir.
                assertThat(outboxRelay.publishPendingBatch(10))
                                .isEqualTo(1);

                ConsumerRecord<String, String> creditRecord = awaitRecord(
                                TransferKafkaTopics.CREDIT_REQUESTED,
                                transfer.getCorrelationId());

                JsonNode creditCommand = objectMapper.readTree(creditRecord.value());

                assertThat(creditRecord.key())
                                .isEqualTo(transfer.getCorrelationId().toString());

                assertThat(creditCommand.path("transferId").asText())
                                .isEqualTo(transfer.getId().toString());

                assertThat(creditCommand.path("accountId").asText())
                                .isEqualTo(transfer.getToAccountId().toString());

                assertThat(creditCommand.path("messageId").asText())
                                .isNotBlank();

                assertThat(creditCommand.path("idempotencyKey").asText())
                                .isNotBlank();

                assertThat(creditCommand.path("amount").decimalValue())
                                .isEqualByComparingTo("125.00");

                assertThat(outboxCount(
                                transfer.getId(),
                                "credit-requested",
                                "PUBLISHED")).isEqualTo(1L);
        }

        @Test
        void shouldCompensateDebitWhenCreditFails() throws Exception {

                // 1. Transfer başlatılır, ilk debit komutu Kafka'ya gönderilir.
                Transfer transfer = transferCreationService.create(
                                new InitiateTransferUseCase.InitiateTransferCommand(
                                                new BigDecimal("250.00"),
                                                "TRY",
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                "kafka-compensation-" + UUID.randomUUID()));

                assertThat(transfer.getStatus().name())
                                .isEqualTo("DEBIT_REQUESTED");

                assertThat(outboxRelay.publishPendingBatch(10))
                                .isEqualTo(1);

                awaitRecord(
                                TransferKafkaTopics.DEBIT_REQUESTED,
                                transfer.getCorrelationId());

                // 2. Debit'in başarıyla tamamlandığını Kafka üzerinden bildir.
                DebitCompletedEvent debitCompletedEvent = new DebitCompletedEvent(
                                UUID.randomUUID(),
                                transfer.getCorrelationId(),
                                UUID.randomUUID(),
                                Instant.now());

                kafkaTemplate.send(
                                TransferKafkaTopics.DEBIT_COMPLETED,
                                transfer.getCorrelationId().toString(),
                                objectMapper.writeValueAsString(debitCompletedEvent)).get(10, TimeUnit.SECONDS);

                // 3. Listener'ın credit komutunu oluşturmasını bekle.
                await()
                                .atMost(Duration.ofSeconds(20))
                                .untilAsserted(() -> {
                                        assertThat(transferStatus(transfer.getId()))
                                                        .isEqualTo("CREDIT_REQUESTED");

                                        assertThat(sagaStep(transfer.getId()))
                                                        .isEqualTo("CREDIT");

                                        assertThat(outboxCount(
                                                        transfer.getId(),
                                                        "credit-requested",
                                                        "PENDING")).isEqualTo(1L);
                                });

                assertThat(outboxRelay.publishPendingBatch(10))
                                .isEqualTo(1);

                ConsumerRecord<String, String> creditRecord = awaitRecord(
                                TransferKafkaTopics.CREDIT_REQUESTED,
                                transfer.getCorrelationId());

                JsonNode creditCommand = objectMapper.readTree(creditRecord.value());

                assertThat(creditRecord.key())
                                .isEqualTo(transfer.getCorrelationId().toString());

                assertThat(creditCommand.path("transferId").asText())
                                .isEqualTo(transfer.getId().toString());

                // 4. Bank B'nin credit işlemini gerçekleştiremediğini bildir.
                String failureReason = "CREDIT_DESTINATION_UNAVAILABLE";

                CreditFailedEvent creditFailedEvent = new CreditFailedEvent(
                                UUID.randomUUID(),
                                transfer.getCorrelationId(),
                                failureReason,
                                Instant.now());

                kafkaTemplate.send(
                                TransferKafkaTopics.CREDIT_FAILED,
                                transfer.getCorrelationId().toString(),
                                objectMapper.writeValueAsString(creditFailedEvent)).get(10, TimeUnit.SECONDS);

                // 5. Saga COMPENSATION adımına geçmeli ve telafi komutu yazılmalı.
                await()
                                .atMost(Duration.ofSeconds(20))
                                .untilAsserted(() -> {
                                        assertThat(transferStatus(transfer.getId()))
                                                        .isEqualTo("COMPENSATING");

                                        assertThat(sagaStep(transfer.getId()))
                                                        .isEqualTo("COMPENSATION");

                                        assertThat(inboxCount(
                                                        creditFailedEvent.eventId())).isEqualTo(1L);

                                        assertThat(outboxCount(
                                                        transfer.getId(),
                                                        "debit-compensate",
                                                        "PENDING")).isEqualTo(1L);
                                });

                // 6. Debit telafi komutunu gerçek Kafka'ya gönder.
                assertThat(outboxRelay.publishPendingBatch(10))
                                .isEqualTo(1);

                ConsumerRecord<String, String> compensationRecord = awaitRecord(
                                TransferKafkaTopics.DEBIT_COMPENSATE,
                                transfer.getCorrelationId());

                JsonNode compensationCommand = objectMapper.readTree(compensationRecord.value());

                assertThat(compensationRecord.key())
                                .isEqualTo(transfer.getCorrelationId().toString());

                assertThat(compensationCommand.path("transferId").asText())
                                .isEqualTo(transfer.getId().toString());

                assertThat(compensationCommand.path("accountId").asText())
                                .isEqualTo(transfer.getFromAccountId().toString());

                assertThat(compensationCommand.path("reason").asText())
                                .isEqualTo(failureReason);

                assertThat(compensationCommand.path("amount").decimalValue())
                                .isEqualByComparingTo("250.00");

                assertThat(compensationCommand.path("messageId").asText())
                                .isNotBlank();

                assertThat(compensationCommand.path("idempotencyKey").asText())
                                .isNotBlank();

                assertThat(outboxCount(
                                transfer.getId(),
                                "debit-compensate",
                                "PUBLISHED")).isEqualTo(1L);

                // 7. Bank A'nın debit telafisini tamamladığını bildir.
                DebitCompensatedEvent debitCompensatedEvent = new DebitCompensatedEvent(
                                UUID.randomUUID(),
                                transfer.getCorrelationId(),
                                UUID.randomUUID(),
                                Instant.now());

                kafkaTemplate.send(
                                TransferKafkaTopics.DEBIT_COMPENSATED,
                                transfer.getCorrelationId().toString(),
                                objectMapper.writeValueAsString(debitCompensatedEvent)).get(10, TimeUnit.SECONDS);

                // 8. Saga terminal COMPENSATED durumuna ulaşmalı.
                await()
                                .atMost(Duration.ofSeconds(20))
                                .untilAsserted(() -> {
                                        assertThat(transferStatus(transfer.getId()))
                                                        .isEqualTo("COMPENSATED");

                                        assertThat(sagaStep(transfer.getId()))
                                                        .isEqualTo("FINISHED");

                                        assertThat(inboxCount(
                                                        debitCompensatedEvent.eventId())).isEqualTo(1L);
                                });
        }

        private ConsumerRecord<String, String> awaitRecord(
                        String topic,
                        UUID correlationId) throws Exception {

                Properties properties = new Properties();
                properties.put(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                KAFKA.getBootstrapServers());
                properties.put(
                                ConsumerConfig.GROUP_ID_CONFIG,
                                "verification-" + UUID.randomUUID());
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

        private String transferStatus(UUID transferId) {
                return jdbcTemplate.queryForObject(
                                "SELECT status FROM transfers WHERE id = ?",
                                String.class,
                                transferId);
        }

        private String sagaStep(UUID transferId) {
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

        private Long outboxCount(
                        UUID transferId,
                        String eventType,
                        String status) {
                return jdbcTemplate.queryForObject(
                                """
                                                SELECT COUNT(*)
                                                FROM outbox_events
                                                WHERE aggregate_id = ?
                                                  AND event_type = ?
                                                  AND status = ?
                                                """,
                                Long.class,
                                transferId,
                                eventType,
                                status);
        }
}