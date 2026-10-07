package com.fintech.transfer.application.service;

import com.fintech.transfer.domain.model.Money;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.domain.port.out.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransferCreationService {

    private final TransferRepository transferRepository;
    private final Clock clock;

    public TransferCreationService(
            TransferRepository transferRepository,
            Clock clock
    ) {
        this.transferRepository = transferRepository;
        this.clock = clock;
    }

    @Transactional
    public Transfer create(
            InitiateTransferUseCase.InitiateTransferCommand command
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

        return transferRepository.save(transfer);
    }
}