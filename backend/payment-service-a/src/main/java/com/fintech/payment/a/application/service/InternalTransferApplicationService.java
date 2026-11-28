package com.fintech.payment.a.application.service;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.fintech.payment.a.application.exception.IdempotencyConflictException;
import com.fintech.payment.a.domain.model.InternalTransfer;
import com.fintech.payment.a.domain.port.out.InternalTransferRepository;

@Service
public class InternalTransferApplicationService {

    private final InternalTransferRepository internalTransferRepository;
    private final InternalTransferTransactionService transactionService;

    public InternalTransferApplicationService(
            InternalTransferRepository internalTransferRepository,
            InternalTransferTransactionService transactionService) {
        this.internalTransferRepository = internalTransferRepository;
        this.transactionService = transactionService;
    }

    public InternalTransferResult execute(
            String ownerSubject,
            InternalTransferCommand command) {

        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException(
                    "ownerSubject must not be blank");
        }

        if (command == null) {
            throw new IllegalArgumentException(
                    "command must not be null");
        }

        String requestFingerprint =
                InternalTransferRequestFingerprint.from(command);

        /*
         * Fast path for an already completed request.
         */
        Optional<InternalTransfer> existing =
                internalTransferRepository.findByOwnerSubjectAndIdempotencyKey(
                        ownerSubject,
                        command.idempotencyKey());

        if (existing.isPresent()) {
            return toReplayResult(
                    existing.get(),
                    requestFingerprint);
        }

        try {
            return transactionService.execute(
                    ownerSubject,
                    command,
                    requestFingerprint);

        } catch (DataIntegrityViolationException exception) {
            /*
             * A concurrent request with the same owner-scoped key
             * may have committed first, even when it used different
             * account IDs and therefore did not share our account locks.
             *
             * The transaction proxy has already rolled back the failed
             * transaction before control reaches this catch block.
             */
            Optional<InternalTransfer> committed =
                    internalTransferRepository
                            .findByOwnerSubjectAndIdempotencyKey(
                                    ownerSubject,
                                    command.idempotencyKey());

            if (committed.isEmpty()) {
                throw exception;
            }

            return toReplayResult(
                    committed.get(),
                    requestFingerprint);
        }
    }

    private InternalTransferResult toReplayResult(
            InternalTransfer existing,
            String requestFingerprint) {

        if (!existing.requestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyConflictException();
        }

        return new InternalTransferResult(existing, true);
    }
}
