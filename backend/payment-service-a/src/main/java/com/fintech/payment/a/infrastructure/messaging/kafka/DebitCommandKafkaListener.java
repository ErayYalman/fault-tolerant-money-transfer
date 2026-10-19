package com.fintech.payment.a.infrastructure.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.payment.a.application.messaging.command.DebitRequestedCommand;
import com.fintech.payment.a.application.service.DebitCommandHandlingService;
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
public class DebitCommandKafkaListener {

    private static final Logger log =
            LoggerFactory.getLogger(DebitCommandKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final DebitCommandHandlingService handlingService;

    public DebitCommandKafkaListener(
            ObjectMapper objectMapper,
            DebitCommandHandlingService handlingService
    ) {
        this.objectMapper = objectMapper;
        this.handlingService = handlingService;
    }

    @KafkaListener(topics = PaymentKafkaTopics.DEBIT_REQUESTED)
    public void onDebitRequested(String payload) {
        final DebitRequestedCommand command;

        try {
            command = objectMapper.readValue(
                    payload,
                    DebitRequestedCommand.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid debit-requested command payload",
                    exception
            );
        }

        DebitCommandHandlingService.Result result =
                handlingService.handle(command);

        if (result == DebitCommandHandlingService.Result.DUPLICATE) {
            log.info(
                    "Ignoring duplicate debit command for correlationId {}",
                    command.correlationId()
            );
        } else if (result == DebitCommandHandlingService.Result.BUSINESS_FAILURE) {
            log.info(
                    "Debit command rejected for correlationId {}",
                    command.correlationId()
            );
        }
    }
}