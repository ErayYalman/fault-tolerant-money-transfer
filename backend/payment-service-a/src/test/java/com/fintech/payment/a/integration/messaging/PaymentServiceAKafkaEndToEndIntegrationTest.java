package com.fintech.payment.a.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.payment.a.application.messaging.command.DebitCompensationCommand;
import com.fintech.payment.a.application.messaging.command.DebitRequestedCommand;
import com.fintech.payment.a.application.outbox.OutboxRelay;
import com.fintech.payment.a.infrastructure.messaging.kafka.PaymentKafkaTopics;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.admin.NewTopic;
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
        "spring.kafka.consumer.group-id=payment-service-a-e2e-test",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(PaymentServiceAKafkaEndToEndIntegrationTest.KafkaTopicsTestConfiguration.class)
class PaymentServiceAKafkaEndToEndIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("payment_a_kafka_e2e_test_db")
                    .withUsername("payment_a_kafka_e2e_user")
                    .withPassword("payment_a_kafka_e2e_password");

    @SuppressWarnings("resource")
    @Container
    static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(
                    DockerImageName.parse("confluentinc/cp-kafka:7.8.1")
            );

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.kafka.bootstrap-servers",
                KAFKA::getBootstrapServers
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxRelay outboxRelay;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM inbox_messages");
        jdbcTemplate.update("DELETE FROM ledger_entries");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    void shouldProcessDebitAndCompensationThroughKafkaEndToEnd()
            throws Exception {

        // Arrange: Bank A hesabında 100 TRY bulunsun.
        UUID accountId = createAccount(new BigDecimal("100.00"));
        UUID transferId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        DebitRequestedCommand debitCommand = new DebitRequestedCommand(
                UUID.randomUUID(),
                correlationId,
                transferId,
                "debit-e2e-" + UUID.randomUUID(),
                accountId,
                new BigDecimal("25.00"),
                "TRY"
        );

        // 1. Debit komutunu gerçek Kafka broker'ına gönder.
        kafkaTemplate.send(
                PaymentKafkaTopics.DEBIT_REQUESTED,
                correlationId.toString(),
                objectMapper.writeValueAsString(debitCommand)
        ).get(10, TimeUnit.SECONDS);

        // 2. Gerçek listener komutu tüketmeli ve işlemi atomik olarak
        // hesap, ledger, inbox ve outbox'a yazmalı.
        await()
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> {
                    assertThat(balance(accountId))
                            .isEqualByComparingTo("75.00");

                    assertThat(countLedger("DEBIT"))
                            .isEqualTo(1L);

                    assertThat(countInboxCommand("debit-requested"))
                            .isEqualTo(1L);

                    assertThat(countOutbox(
                            "debit-completed",
                            "PENDING"))
                            .isEqualTo(1L);
                });

        // 3. Outbox'taki debit-completed olayını Kafka'ya yayınla.
        assertThat(outboxRelay.publishPendingBatch(10))
                .isEqualTo(1);

        ConsumerRecord<String, String> debitEventRecord = awaitRecord(
                PaymentKafkaTopics.DEBIT_COMPLETED,
                correlationId
        );

        JsonNode debitEvent =
                objectMapper.readTree(debitEventRecord.value());

        assertThat(debitEventRecord.key())
                .isEqualTo(correlationId.toString());

        assertThat(debitEvent.path("eventId").asText())
                .isNotBlank();

        assertThat(debitEvent.path("correlationId").asText())
                .isEqualTo(correlationId.toString());

        String debitLedgerEntryId = ledgerEntryId("DEBIT");

        assertThat(debitEvent.path("ledgerEntryId").asText())
                .isEqualTo(debitLedgerEntryId);

        assertThat(countOutbox(
                "debit-completed",
                "PUBLISHED"))
                .isEqualTo(1L);

        // 4. Aynı transfer için debit-compensate komutunu gönder.
        DebitCompensationCommand compensationCommand =
                new DebitCompensationCommand(
                        UUID.randomUUID(),
                        correlationId,
                        transferId,
                        "compensation-e2e-" + UUID.randomUUID(),
                        accountId,
                        new BigDecimal("25.00"),
                        "TRY",
                        "CREDIT_FAILED"
                );

        kafkaTemplate.send(
                PaymentKafkaTopics.DEBIT_COMPENSATE,
                correlationId.toString(),
                objectMapper.writeValueAsString(compensationCommand)
        ).get(10, TimeUnit.SECONDS);

        // 5. Gerçek compensation listener'ı bakiyeyi geri almalı,
        // REVERSAL ledger kaydı ve debit-compensated Outbox olayı
        // aynı transaction içinde yazılmalı.
        await()
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> {
                    assertThat(balance(accountId))
                            .isEqualByComparingTo("100.00");

                    assertThat(countLedger("DEBIT"))
                            .isEqualTo(1L);

                    assertThat(countLedger("REVERSAL"))
                            .isEqualTo(1L);

                    assertThat(countInboxCommand("debit-compensate"))
                            .isEqualTo(1L);

                    assertThat(countOutbox(
                            "debit-compensated",
                            "PENDING"))
                            .isEqualTo(1L);
                });

        // 6. Telafi olayını da Outbox üzerinden gerçek Kafka'ya yayınla.
        assertThat(outboxRelay.publishPendingBatch(10))
                .isEqualTo(1);

        ConsumerRecord<String, String> compensationEventRecord =
                awaitRecord(
                        PaymentKafkaTopics.DEBIT_COMPENSATED,
                        correlationId
                );

        JsonNode compensationEvent =
                objectMapper.readTree(compensationEventRecord.value());

        assertThat(compensationEventRecord.key())
                .isEqualTo(correlationId.toString());

        assertThat(compensationEvent.path("eventId").asText())
                .isNotBlank();

        assertThat(compensationEvent.path("correlationId").asText())
                .isEqualTo(correlationId.toString());

        String reversalLedgerEntryId = ledgerEntryId("REVERSAL");

        assertThat(
                compensationEvent.path("reversalLedgerEntryId").asText()
        ).isEqualTo(reversalLedgerEntryId);

        assertThat(countOutbox(
                "debit-compensated",
                "PUBLISHED"))
                .isEqualTo(1L);

        // Son bakiye ve ledger tutarlılığı.
        assertThat(balance(accountId))
                .isEqualByComparingTo("100.00");

        assertThat(countLedgerTotal())
                .isEqualTo(2L);
    }

    private UUID createAccount(BigDecimal initialBalance) {
        UUID accountId = UUID.randomUUID();

        jdbcTemplate.update(
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
                VALUES (?, ?, ?, 'TRY', 'ACTIVE', 0, NOW())
                """,
                accountId,
                "E2E-" + UUID.randomUUID(),
                initialBalance
        );

        return accountId;
    }

    private BigDecimal balance(UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal.class,
                accountId
        );
    }

    private long countLedger(String entryType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE entry_type = ?",
                Long.class,
                entryType
        );
    }

    private long countLedgerTotal() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries",
                Long.class
        );
    }

    private String ledgerEntryId(String entryType) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id::text
                FROM ledger_entries
                WHERE entry_type = ?
                """,
                String.class,
                entryType
        );
    }

    private long countInboxCommand(String commandType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_messages WHERE command_type = ?",
                Long.class,
                commandType
        );
    }

    private long countOutbox(String eventType, String status) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE event_type = ?
                  AND status = ?
                """,
                Long.class,
                eventType,
                status
        );
    }

    private ConsumerRecord<String, String> awaitRecord(
            String topic,
            UUID correlationId
    ) throws Exception {

        Properties properties = new Properties();
        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "payment-a-e2e-verification-" + UUID.randomUUID()
        );
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );

        long deadline = System.nanoTime()
                + Duration.ofSeconds(20).toNanos();

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(properties)) {

            consumer.subscribe(List.of(topic));

            while (System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofMillis(250));

                for (ConsumerRecord<String, String> record : records) {
                    JsonNode payload =
                            objectMapper.readTree(record.value());

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
                        + correlationId
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaTopicsTestConfiguration {

        @Bean
        NewTopic debitRequestedTestTopic() {
            return TopicBuilder
                    .name(PaymentKafkaTopics.DEBIT_REQUESTED)
                    .partitions(1)
                    .replicas(1)
                    .build();
        }

        @Bean
        NewTopic debitCompensateTestTopic() {
            return TopicBuilder
                    .name(PaymentKafkaTopics.DEBIT_COMPENSATE)
                    .partitions(1)
                    .replicas(1)
                    .build();
        }

        @Bean
        NewTopic debitCompletedTestTopic() {
            return TopicBuilder
                    .name(PaymentKafkaTopics.DEBIT_COMPLETED)
                    .partitions(1)
                    .replicas(1)
                    .build();
        }

        @Bean
        NewTopic debitFailedTestTopic() {
            return TopicBuilder
                    .name(PaymentKafkaTopics.DEBIT_FAILED)
                    .partitions(1)
                    .replicas(1)
                    .build();
        }

        @Bean
        NewTopic debitCompensatedTestTopic() {
            return TopicBuilder
                    .name(PaymentKafkaTopics.DEBIT_COMPENSATED)
                    .partitions(1)
                    .replicas(1)
                    .build();
        }
    }
}