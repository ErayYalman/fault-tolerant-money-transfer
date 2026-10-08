package com.fintech.transfer.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.transfer.domain.model.Money;
import com.fintech.transfer.domain.model.SagaState;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.domain.port.out.SagaStateRepository;
import com.fintech.transfer.domain.port.out.TransferRepository;

@Service
public class TransferCreationService {

        private final TransferRepository transferRepository;
        private final Clock clock;
        private final SagaStateRepository sagaStateRepository;

        public TransferCreationService(
                        TransferRepository transferRepository,
                        Clock clock,
                        SagaStateRepository sagaStateRepository) {
                this.transferRepository = transferRepository;
                this.clock = clock;
                this.sagaStateRepository = sagaStateRepository;
        }

        @Transactional
        public Transfer create(
                        InitiateTransferUseCase.InitiateTransferCommand command) {
                Instant now = Instant.now(clock);

                Transfer transfer = Transfer.create(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                command.idempotencyKey(),
                                command.fromAccountId(),
                                command.toAccountId(),
                                new Money(
                                                command.amount(),
                                                command.currency()),
                                now);

                Transfer savedTransfer = transferRepository.save(transfer);

                SagaState sagaState = SagaState.start(
                                UUID.randomUUID(),
                                savedTransfer.getId(),
                                now);

                sagaStateRepository.save(sagaState);

                return savedTransfer;
        }
}