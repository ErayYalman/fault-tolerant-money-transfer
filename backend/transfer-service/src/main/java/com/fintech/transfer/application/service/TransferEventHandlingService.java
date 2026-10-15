package com.fintech.transfer.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.transfer.application.messaging.OutboundMessage;
import com.fintech.transfer.application.messaging.command.CreditRequestedCommand;
import com.fintech.transfer.application.messaging.command.DebitCompensationCommand;
import com.fintech.transfer.application.messaging.event.CreditCompletedEvent;
import com.fintech.transfer.application.messaging.event.CreditFailedEvent;
import com.fintech.transfer.application.messaging.event.DebitCompensatedEvent;
import com.fintech.transfer.application.messaging.event.DebitCompletedEvent;
import com.fintech.transfer.application.messaging.event.DebitFailedEvent;
import com.fintech.transfer.application.port.out.ConsumedEventInbox;
import com.fintech.transfer.application.port.out.DomainEventPublisher;
import com.fintech.transfer.application.saga.SagaAction;
import com.fintech.transfer.application.saga.SagaDecision;
import com.fintech.transfer.application.saga.TransferSagaOrchestrator;
import com.fintech.transfer.domain.model.Transfer;

@Service
public class TransferEventHandlingService {

    private static final String AGGREGATE_TYPE = "TRANSFER";

    private final ConsumedEventInbox consumedEventInbox;
    private final TransferSagaOrchestrator sagaOrchestrator;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public TransferEventHandlingService(
            ConsumedEventInbox consumedEventInbox,
            TransferSagaOrchestrator sagaOrchestrator,
            DomainEventPublisher domainEventPublisher,
            Clock clock
    ) {
        this.consumedEventInbox = consumedEventInbox;
        this.sagaOrchestrator = sagaOrchestrator;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public boolean handle(DebitCompletedEvent event) {
        if (!register(event.eventId(), "debit-completed",
                event.correlationId())) {
            return false;
        }

        Instant creditRequestedAt = Instant.now(clock);

        SagaDecision decision = sagaOrchestrator.onDebitCompleted(
                event.correlationId(),
                event.processedAt(),
                creditRequestedAt
        );

        requireAction(
                decision,
                SagaAction.REQUEST_CREDIT,
                "debit-completed"
        );

        Transfer transfer = decision.transfer();
        CreditRequestedCommand command =
                CreditRequestedCommand.from(transfer);

        publishCommand(
                transfer,
                "credit-requested",
                command.toPayload()
        );

        return true;
    }

    @Transactional
    public boolean handle(DebitFailedEvent event) {
        if (!register(event.eventId(), "debit-failed",
                event.correlationId())) {
            return false;
        }

        SagaDecision decision = sagaOrchestrator.onDebitFailed(
                event.correlationId(),
                event.failureReason(),
                event.failedAt()
        );

        requireAction(
                decision,
                SagaAction.NONE,
                "debit-failed"
        );

        return true;
    }

    @Transactional
    public boolean handle(CreditCompletedEvent event) {
        if (!register(event.eventId(), "credit-completed",
                event.correlationId())) {
            return false;
        }

        SagaDecision decision = sagaOrchestrator.onCreditCompleted(
                event.correlationId(),
                event.processedAt()
        );

        requireAction(
                decision,
                SagaAction.NONE,
                "credit-completed"
        );

        return true;
    }

    @Transactional
    public boolean handle(CreditFailedEvent event) {
        if (!register(event.eventId(), "credit-failed",
                event.correlationId())) {
            return false;
        }

        Instant compensationRequestedAt = Instant.now(clock);

        SagaDecision decision = sagaOrchestrator.onCreditFailed(
                event.correlationId(),
                event.failureReason(),
                event.failedAt(),
                compensationRequestedAt
        );

        requireAction(
                decision,
                SagaAction.REQUEST_DEBIT_COMPENSATION,
                "credit-failed"
        );

        Transfer transfer = decision.transfer();
        DebitCompensationCommand command =
                DebitCompensationCommand.from(transfer);

        publishCommand(
                transfer,
                "debit-compensate",
                command.toPayload()
        );

        return true;
    }

    @Transactional
    public boolean handle(DebitCompensatedEvent event) {
        if (!register(event.eventId(), "debit-compensated",
                event.correlationId())) {
            return false;
        }

        SagaDecision decision = sagaOrchestrator.onDebitCompensated(
                event.correlationId(),
                event.compensatedAt()
        );

        requireAction(
                decision,
                SagaAction.NONE,
                "debit-compensated"
        );

        return true;
    }

    private boolean register(
            UUID eventId,
            String eventType,
            UUID correlationId
    ) {
        return consumedEventInbox.tryRegister(
                eventId,
                eventType,
                correlationId
        );
    }

    private void publishCommand(
            Transfer transfer,
            String eventType,
            java.util.Map<String, Object> payload
    ) {
        domainEventPublisher.publish(
                new OutboundMessage(
                        AGGREGATE_TYPE,
                        transfer.getId(),
                        eventType,
                        payload
                )
        );
    }

    private void requireAction(
            SagaDecision decision,
            SagaAction expectedAction,
            String eventType
    ) {
        if (decision.action() != expectedAction) {
            throw new IllegalStateException(
                    "Unexpected Saga action for " + eventType
                            + ": expected " + expectedAction
                            + " but received " + decision.action()
            );
        }
    }
}