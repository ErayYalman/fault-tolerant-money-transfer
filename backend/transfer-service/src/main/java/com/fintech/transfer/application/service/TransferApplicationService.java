package com.fintech.transfer.application.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.transfer.domain.exception.IdempotencyConflictException;
import com.fintech.transfer.domain.exception.RecipientAccountNotActiveException;
import com.fintech.transfer.domain.exception.RecipientCurrencyMismatchException;
import com.fintech.transfer.domain.exception.TransferNotFoundException;
import com.fintech.transfer.domain.model.RecipientAccount;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.in.GetTransferUseCase;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.domain.port.in.LookupRecipientAccountUseCase;
import com.fintech.transfer.domain.port.out.TransferRepository;

@Service
public class TransferApplicationService implements InitiateTransferUseCase, GetTransferUseCase {

        private final TransferRepository transferRepository;
        private final TransferCreationService transferCreationService;
        private final LookupRecipientAccountUseCase recipientAccountLookup;

        public TransferApplicationService(
                        TransferRepository transferRepository,
                        TransferCreationService transferCreationService,
                        LookupRecipientAccountUseCase recipientAccountLookup) {
                this.transferRepository = transferRepository;
                this.transferCreationService = transferCreationService;
                this.recipientAccountLookup = recipientAccountLookup;
        }

        @Override
        public CreateTransferResult execute(
                        InitiateTransferCommand command) {
                String requestFingerprint = TransferRequestFingerprint.from(command);

                return transferRepository
                                .findByOwnerSubjectAndIdempotencyKey(
                                                command.ownerSubject(),
                                                command.idempotencyKey())
                                .map(existingTransfer -> returnExistingTransfer(
                                                existingTransfer,
                                                requestFingerprint))
                                .orElseGet(() -> createWithConcurrentSafety(
                                                command,
                                                requestFingerprint));
        }

        private CreateTransferResult returnExistingTransfer(
                        Transfer existingTransfer,
                        String requestFingerprint) {
                String existingFingerprint = existingTransfer.getRequestFingerprint();

                if (existingFingerprint != null
                                && !existingFingerprint.equals(requestFingerprint)) {
                        throw new IdempotencyConflictException();
                }

                return new CreateTransferResult(existingTransfer, false);
        }

        private CreateTransferResult createWithConcurrentSafety(
                        InitiateTransferCommand command,
                        String requestFingerprint) {

                RecipientAccount recipient = recipientAccountLookup
                                .findByAccountNumber(command.toAccountNumber());

                if (!recipient.isActive()) {
                        throw new RecipientAccountNotActiveException();
                }

                if (!recipient.supportsCurrency(command.currency())) {
                        throw new RecipientCurrencyMismatchException();
                }

                try {
                        Transfer transfer = transferCreationService.create(
                                        command,
                                        recipient.accountId());

                        return new CreateTransferResult(
                                        transfer,
                                        true);
                } catch (DataIntegrityViolationException exception) {

                        return transferRepository
                                        .findByOwnerSubjectAndIdempotencyKey(
                                                        command.ownerSubject(),
                                                        command.idempotencyKey())
                                        .map(existingTransfer -> returnExistingTransfer(
                                                        existingTransfer,
                                                        requestFingerprint))
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