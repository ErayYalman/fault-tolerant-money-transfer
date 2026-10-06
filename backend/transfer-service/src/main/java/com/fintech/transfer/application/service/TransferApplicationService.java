package com.fintech.transfer.application.service;

import com.fintech.transfer.domain.exception.TransferNotFoundException;
import com.fintech.transfer.domain.model.Money;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.in.GetTransferUseCase;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.domain.port.out.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class TransferApplicationService implements InitiateTransferUseCase, GetTransferUseCase {

    private final TransferRepository transferRepository;
    private final Clock clock;

    public TransferApplicationService(
            TransferRepository transferRepository,
            Clock clock
    ) {
        this.transferRepository = transferRepository;
        this.clock = clock;
    }

    @Override
    public CreateTransferResult execute(InitiateTransferCommand command) {

        return transferRepository
                .findByIdempotencyKey(command.idempotencyKey())
                .map(existingTransfer ->
                        new CreateTransferResult(existingTransfer, false)
                )
                .orElseGet(() -> createTransfer(command));
    }

    private CreateTransferResult createTransfer(
            InitiateTransferCommand command
    ) {
        Instant now = Instant.now(clock);

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

        return new CreateTransferResult(
                savedTransfer,
                true
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Transfer getById(UUID transferId) {
        return transferRepository
                .findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
    }
}