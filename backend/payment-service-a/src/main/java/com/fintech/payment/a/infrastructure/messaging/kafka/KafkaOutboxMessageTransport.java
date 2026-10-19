package com.fintech.payment.a.infrastructure.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.payment.a.application.messaging.PendingOutboxMessage;
import com.fintech.payment.a.application.port.out.OutboxMessageTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(
        name = "app.kafka.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class KafkaOutboxMessageTransport
        implements OutboxMessageTransport {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentKafkaTopicResolver topicResolver;
    private final long sendTimeoutMs;

    public KafkaOutboxMessageTransport(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            PaymentKafkaTopicResolver topicResolver,
            @Value("${app.outbox.send-timeout-ms:10000}")
            long sendTimeoutMs
    ) {
        if (sendTimeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "sendTimeoutMs must be positive"
            );
        }

        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topicResolver = topicResolver;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Override
    public void publish(PendingOutboxMessage message) {
        Object correlationId = message.payload().get("correlationId");

        if (!(correlationId instanceof String key) || key.isBlank()) {
            throw new OutboxTransportException(
                    "Outbox payload must contain a non-empty correlationId",
                    null
            );
        }

        final String payload;

        try {
            payload = objectMapper.writeValueAsString(message.payload());
        } catch (JsonProcessingException exception) {
            throw new OutboxTransportException(
                    "Could not serialize outbox message " + message.id(),
                    exception
            );
        }

        String topic = topicResolver.resolve(message.eventType());

        try {
            kafkaTemplate.send(topic, key, payload)
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OutboxTransportException(
                    "Kafka publication was interrupted for message " + message.id(),
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            throw new OutboxTransportException(
                    "Kafka publication failed for message " + message.id(),
                    exception
            );
        }
    }
}