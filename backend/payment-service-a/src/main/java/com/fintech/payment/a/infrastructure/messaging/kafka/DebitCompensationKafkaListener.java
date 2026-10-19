package com.fintech.payment.a.infrastructure.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.payment.a.application.messaging.command.DebitCompensationCommand;
import com.fintech.payment.a.application.service.DebitCompensationHandlingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.kafka.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DebitCompensationKafkaListener {

    private static final Logger log =
            LoggerFactory.getLogger(DebitCompensationKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final DebitCompensationHandlingService handlingService;

    public DebitCompensationKafkaListener(
            ObjectMapper objectMapper,
            DebitCompensationHandlingService handlingService
    ) {
        this.objectMapper = objectMapper;
        this.handlingService = handlingService;
    }

    @KafkaListener(topics = PaymentKafkaTopics.DEBIT_COMPENSATE)
    public void onDebitCompensate(String payload) {
        final DebitCompensationCommand command;

        try {
            command = objectMapper.readValue(
                    payload,
                    DebitCompensationCommand.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid debit-compensate command payload",
                    exception
            );
        }

        DebitCompensationHandlingService.Result result =
                handlingService.handle(command);

        if (result == DebitCompensationHandlingService.Result.DUPLICATE) {
            log.info(
                    "Ignoring duplicate debit-compensation command for correlationId {}",
                    command.correlationId()
            );
        }
    }
}