package com.fintech.transfer.application.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.transfer.domain.exception.TransferNotFoundException;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.in.GetTransferUseCase;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.domain.port.out.TransferRepository;

@Service
public class TransferApplicationService implements InitiateTransferUseCase, GetTransferUseCase {

        private final TransferRepository transferRepository;
        private final TransferCreationService transferCreationService;

        public TransferApplicationService(
                        TransferRepository transferRepository,
                        TransferCreationService transferCreationService) {
                this.transferRepository = transferRepository;
                this.transferCreationService = transferCreationService;
        }

        @Override
        public CreateTransferResult execute(
                        InitiateTransferCommand command) {
                return transferRepository
                                .findByOwnerSubjectAndIdempotencyKey(
                                                command.ownerSubject(),
                                                command.idempotencyKey())
                                .map(existingTransfer -> new CreateTransferResult(
                                                existingTransfer,
                                                false))
                                .orElseGet(() -> createWithConcurrentSafety(command));
        }

        private CreateTransferResult createWithConcurrentSafety(
                        InitiateTransferCommand command) {
                try {
                        Transfer transfer = transferCreationService.create(command);

                        return new CreateTransferResult(
                                        transfer,
                                        true);
                } catch (DataIntegrityViolationException exception) {

                        return transferRepository
                                        .findByOwnerSubjectAndIdempotencyKey(
                                                        command.ownerSubject(),
                                                        command.idempotencyKey())
                                        .map(existingTransfer -> new CreateTransferResult(
                                                        existingTransfer,
                                                        false))
                                        .orElseThrow(() -> exception);
                }
        }

        @Override
        @Transactional(readOnly = true)
        public Transfer getById(UUID transferId, String ownerSubject) {
                return transferRepository
                                .findByIdAndOwnerSubject(transferId, ownerSubject)
                                .orElseThrow(
                                                () -> new TransferNotFoundException(transferId));
        }
}