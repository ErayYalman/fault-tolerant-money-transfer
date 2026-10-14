package com.fintech.transfer.application.service;

import com.fintech.transfer.application.messaging.OutboundMessage;
import com.fintech.transfer.application.messaging.command.DebitRequestedCommand;
import com.fintech.transfer.application.port.out.DomainEventPublisher;
import com.fintech.transfer.application.saga.SagaAction;
import com.fintech.transfer.application.saga.SagaDecision;
import com.fintech.transfer.application.saga.TransferSagaOrchestrator;
import com.fintech.transfer.domain.model.Money;
import com.fintech.transfer.domain.model.SagaState;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.domain.port.out.SagaStateRepository;
import com.fintech.transfer.domain.port.out.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransferCreationService {

    private final TransferRepository transferRepository;
    private final SagaStateRepository sagaStateRepository;
    private final TransferSagaOrchestrator sagaOrchestrator;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public TransferCreationService(
            TransferRepository transferRepository,
            SagaStateRepository sagaStateRepository,
            TransferSagaOrchestrator sagaOrchestrator,
            DomainEventPublisher domainEventPublisher,
            Clock clock
    ) {
        this.transferRepository = transferRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.sagaOrchestrator = sagaOrchestrator;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Transfer create(
            InitiateTransferUseCase.InitiateTransferCommand command
    ) {
        Instant now = Instant.now(clock);

        // 1. Transfer initially enters PENDING.
        Transfer transfer = Transfer.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                command.idempotencyKey(),
                command.fromAccountId(),
                command.toAccountId(),
                new Money(
                        command.amount(),
                        command.currency()
                ),
                now
        );

        Transfer savedTransfer = transferRepository.save(transfer);

        // 2. Initialize orchestration context.
        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                savedTransfer.getId(),
                now
        );

        sagaStateRepository.save(sagaState);

        // 3. Move the transfer to DEBIT_REQUESTED.
        SagaDecision decision = sagaOrchestrator.startDebit(
                savedTransfer.getId(),
                now
        );

        if (decision.action() != SagaAction.REQUEST_DEBIT) {
            throw new IllegalStateException(
                    "Expected REQUEST_DEBIT action but received "
                            + decision.action()
            );
        }

        DebitRequestedCommand debitCommand =
                DebitRequestedCommand.from(decision.transfer());

        domainEventPublisher.publish(
                new OutboundMessage(
                        "TRANSFER",
                        decision.transfer().getId(),
                        "debit-requested",
                        debitCommand.toPayload()
                )
        );

        return decision.transfer();
    }
}