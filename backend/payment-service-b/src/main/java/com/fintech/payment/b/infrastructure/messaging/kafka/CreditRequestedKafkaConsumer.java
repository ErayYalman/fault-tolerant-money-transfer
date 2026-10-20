package com.fintech.payment.b.infrastructure.messaging.kafka;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.payment.b.application.messaging.command.CreditRequestedCommand;
import com.fintech.payment.b.application.service.CreditCommandHandlingService;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CreditRequestedKafkaConsumer {

    private static final Logger LOG = Logger.getLogger(CreditRequestedKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final CreditCommandHandlingService handlingService;

    public CreditRequestedKafkaConsumer(
            ObjectMapper objectMapper,
            CreditCommandHandlingService handlingService) {
        this.objectMapper = objectMapper;
        this.handlingService = handlingService;
    }

    @Incoming("credit-requested")
    @Blocking
    public void onCreditRequested(String payload) {
        final CreditRequestedCommand command;

        try {
            command = objectMapper.readValue(
                    payload,
                    CreditRequestedCommand.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid credit-requested command payload",
                    exception);
        }

        CreditCommandHandlingService.Result result = handlingService.handle(command);

        switch (result) {
            case PROCESSED -> LOG.infof(
                    "Credit command processed for correlationId %s",
                    command.correlationId());

            case BUSINESS_FAILURE -> LOG.infof(
                    "Credit command rejected for correlationId %s",
                    command.correlationId());

            case DUPLICATE -> LOG.infof(
                    "Ignoring duplicate credit command for correlationId %s",
                    command.correlationId());
        }
    }
}