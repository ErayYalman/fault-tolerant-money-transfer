package com.fintech.payment.b.support;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.fintech.payment.b.infrastructure.messaging.kafka.PaymentBKafkaTopics;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class PaymentBKafkaTestResource
        implements QuarkusTestResourceLifecycleManager {

    private final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.8.1"));

    @Override
    public Map<String, String> start() {
        kafka.start();

        try {
            createTopics();
        } catch (Exception exception) {
            kafka.stop();

            throw new IllegalStateException(
                    "Could not create Bank B Kafka test topics",
                    exception);
        }

        String bootstrapServers = kafka.getBootstrapServers();

        return Map.of(
                // Hem genel Kafka hem de SmallRye Kafka için bootstrap adresi
                "kafka.bootstrap.servers",
                bootstrapServers,

                "mp.messaging.connector.smallrye-kafka.bootstrap.servers",
                bootstrapServers,

                // Her test çalışmasında benzersiz group.id ile stale offset çakışmalarını önle
                "mp.messaging.incoming.credit-requested.group.id",
                "payment-service-b-e2e-" + UUID.randomUUID(),

                // Mesajın kaçırılmaması için en baştan okumayı garantile
                "mp.messaging.incoming.credit-requested.auto.offset.reset",
                "earliest");
    }

    private void createTopics() throws Exception {
        Properties properties = new Properties();
        properties.put(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers());

        try (AdminClient admin = AdminClient.create(properties)) {
            admin.createTopics(List.of(
                    new NewTopic(
                            PaymentBKafkaTopics.CREDIT_REQUESTED,
                            1,
                            (short) 1),
                    new NewTopic(
                            PaymentBKafkaTopics.CREDIT_COMPLETED,
                            1,
                            (short) 1),
                    new NewTopic(
                            PaymentBKafkaTopics.CREDIT_FAILED,
                            1,
                            (short) 1)))
                    .all().get(30, TimeUnit.SECONDS);
        }
    }

    @Override
    public void stop() {
        kafka.stop();
    }
}