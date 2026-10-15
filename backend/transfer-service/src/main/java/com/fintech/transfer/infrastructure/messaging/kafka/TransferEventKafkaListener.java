package com.fintech.transfer.infrastructure.messaging.kafka;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.transfer.application.messaging.event.CreditCompletedEvent;
import com.fintech.transfer.application.messaging.event.CreditFailedEvent;
import com.fintech.transfer.application.messaging.event.DebitCompensatedEvent;
import com.fintech.transfer.application.messaging.event.DebitCompletedEvent;
import com.fintech.transfer.application.messaging.event.DebitFailedEvent;
import com.fintech.transfer.application.service.TransferEventHandlingService;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TransferEventKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(TransferEventKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final TransferEventHandlingService eventHandlingService;

    public TransferEventKafkaListener(
            ObjectMapper objectMapper,
            TransferEventHandlingService eventHandlingService) {
        this.objectMapper = objectMapper;
        this.eventHandlingService = eventHandlingService;
    }

    @KafkaListener(topics = TransferKafkaTopics.DEBIT_COMPLETED)
    public void onDebitCompleted(String payload) {
        consume(
                payload,
                DebitCompletedEvent.class,
                "debit-completed",
                eventHandlingService::handle);
    }

    @KafkaListener(topics = TransferKafkaTopics.DEBIT_FAILED)
    public void onDebitFailed(String payload) {
        consume(
                payload,
                DebitFailedEvent.class,
                "debit-failed",
                eventHandlingService::handle);
    }

    @KafkaListener(topics = TransferKafkaTopics.CREDIT_COMPLETED)
    public void onCreditCompleted(String payload) {
        consume(
                payload,
                CreditCompletedEvent.class,
                "credit-completed",
                eventHandlingService::handle);
    }

    @KafkaListener(topics = TransferKafkaTopics.CREDIT_FAILED)
    public void onCreditFailed(String payload) {
        consume(
                payload,
                CreditFailedEvent.class,
                "credit-failed",
                eventHandlingService::handle);
    }

    @KafkaListener(topics = TransferKafkaTopics.DEBIT_COMPENSATED)
    public void onDebitCompensated(String payload) {
        consume(
                payload,
                DebitCompensatedEvent.class,
                "debit-compensated",
                eventHandlingService::handle);
    }

    private <T> void consume(
            String payload,
            Class<T> eventClass,
            String eventType,
            Function<T, Boolean> handler) {
        final T event;

        try {
            event = objectMapper.readValue(payload, eventClass);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid Kafka payload for event type: " + eventType,
                    exception);
        }

        boolean processed = handler.apply(event);

        if (!processed) {
            log.info(
                    "Ignoring duplicate Kafka event of type {}",
                    eventType);
        }
    }
}