package com.fintech.payment.b.infrastructure.messaging.kafka;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.payment.b.application.exception.OutboxTransportException;
import com.fintech.payment.b.application.messaging.PendingOutboxMessage;
import com.fintech.payment.b.application.port.out.OutboxMessageTransport;

import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class KafkaOutboxMessageTransport implements OutboxMessageTransport {

    private final Emitter<String> emitter;
    private final ObjectMapper objectMapper;
    private final PaymentBKafkaTopicResolver topicResolver;
    private final long sendTimeoutMs;

    public KafkaOutboxMessageTransport(
            @Channel("payment-events") Emitter<String> emitter,
            ObjectMapper objectMapper,
            PaymentBKafkaTopicResolver topicResolver,
            @ConfigProperty(
                    name = "app.outbox.send-timeout-ms",
                    defaultValue = "10000"
            ) long sendTimeoutMs
    ) {
        if (sendTimeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "sendTimeoutMs must be positive"
            );
        }

        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.topicResolver = topicResolver;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Override
    public void publish(PendingOutboxMessage message) {
        Object rawCorrelationId = message.payload().get("correlationId");

        if (!(rawCorrelationId instanceof String correlationId)
                || correlationId.isBlank()) {
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
                    "Could not serialize Outbox message " + message.id(),
                    exception
            );
        }

        String topic = topicResolver.resolve(message.eventType());

        /*
         * Emitter.send(Message) has no returned CompletionStage.
         * The Message callbacks let the relay wait for broker acknowledgement.
         */
        CompletableFuture<Void> acknowledgement = new CompletableFuture<>();

        Message<String> kafkaMessage = Message.of(
                payload,
                () -> {
                    acknowledgement.complete(null);
                    return CompletableFuture.completedFuture(null);
                },
                failure -> {
                    acknowledgement.completeExceptionally(failure);
                    return CompletableFuture.completedFuture(null);
                }
        ).addMetadata(
                OutgoingKafkaRecordMetadata.<String>builder()
                        .withKey(correlationId)
                        .withTopic(topic)
                        .build()
        );

        try {
            emitter.send(kafkaMessage);

            acknowledgement.get(
                    sendTimeoutMs,
                    TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new OutboxTransportException(
                    "Kafka publication was interrupted for message "
                            + message.id(),
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            throw new OutboxTransportException(
                    "Kafka publication failed for message " + message.id(),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new OutboxTransportException(
                    "Could not dispatch Kafka message " + message.id(),
                    exception
            );
        }
    }
}