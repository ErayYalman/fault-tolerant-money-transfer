package com.fintech.payment.a.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.payment.a.application.exception.IdempotencyConflictException;
import com.fintech.payment.a.application.exception.InternalTransferAccountNotFoundException;
import com.fintech.payment.a.domain.model.Account;
import com.fintech.payment.a.domain.model.InternalTransfer;
import com.fintech.payment.a.domain.model.LedgerEntry;
import com.fintech.payment.a.domain.model.LedgerEntryStatus;
import com.fintech.payment.a.domain.model.LedgerEntryType;
import com.fintech.payment.a.domain.port.out.AccountRepository;
import com.fintech.payment.a.domain.port.out.InternalTransferRepository;
import com.fintech.payment.a.domain.port.out.LedgerEntryRepository;

@Service
public class InternalTransferTransactionService {

    private final AccountRepository accountRepository;
    private final InternalTransferRepository internalTransferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final Clock clock;

    public InternalTransferTransactionService(
            AccountRepository accountRepository,
            InternalTransferRepository internalTransferRepository,
            LedgerEntryRepository ledgerEntryRepository,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.internalTransferRepository = internalTransferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.clock = clock;
    }

    @Transactional
    public InternalTransferResult execute(
            String ownerSubject,
            InternalTransferCommand command,
            String requestFingerprint) {

        if (command.fromAccountId().equals(command.toAccountId())) {
            throw new IllegalArgumentException(
                    "Source and destination accounts must be different");
        }

        /*
         * First idempotency check:
         * avoid locking accounts when an already committed result exists.
         */
        Optional<InternalTransferResult> existingBeforeLock =
                findExistingResult(
                        ownerSubject,
                        command.idempotencyKey(),
                        requestFingerprint);

        if (existingBeforeLock.isPresent()) {
            return existingBeforeLock.get();
        }

        /*
         * Both account rows are locked in database UUID order.
         * The lock remains active until this transaction commits or rolls back.
         */
        List<Account> lockedAccounts =
                accountRepository.findAllByIdsForUpdate(
                        List.of(
                                command.fromAccountId(),
                                command.toAccountId()));

        if (lockedAccounts.size() != 2) {
            throw new InternalTransferAccountNotFoundException();
        }

        /*
         * Second idempotency check:
         * another request may have completed while this request
         * was waiting for the account locks.
         */
        Optional<InternalTransferResult> existingAfterLock =
                findExistingResult(
                        ownerSubject,
                        command.idempotencyKey(),
                        requestFingerprint);

        if (existingAfterLock.isPresent()) {
            return existingAfterLock.get();
        }

        Map<UUID, Account> accountsById = lockedAccounts.stream()
                .collect(Collectors.toMap(
                        Account::getId,
                        Function.identity()));

        Account sourceAccount = accountsById.get(
                command.fromAccountId());

        Account destinationAccount = accountsById.get(
                command.toAccountId());

        if (sourceAccount == null || destinationAccount == null) {
            throw new InternalTransferAccountNotFoundException();
        }

        /*
         * Do not reveal whether a foreign account exists.
         */
        if (!sourceAccount.isOwnedBy(ownerSubject)
                || !destinationAccount.isOwnedBy(ownerSubject)) {
            throw new InternalTransferAccountNotFoundException();
        }

        Instant now = Instant.now(clock);

        /*
         * Domain methods enforce active account, currency and balance rules.
         * No database update has been made yet. If either operation fails,
         * no account or ledger changes are persisted.
         */
        Account.DebitResult debitResult = sourceAccount.debit(
                command.amount(),
                command.currency(),
                now);

        Account.CreditResult creditResult = destinationAccount.credit(
                command.amount(),
                command.currency(),
                now);

        UUID transferId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        InternalTransfer transfer = new InternalTransfer(
                transferId,
                correlationId,
                ownerSubject,
                sourceAccount.getId(),
                destinationAccount.getId(),
                command.amount(),
                command.currency(),
                command.idempotencyKey(),
                requestFingerprint,
                now);

        /*
         * Every write below participates in this same transaction:
         * two account updates + one transfer record + two ledger records.
         */
        accountRepository.update(sourceAccount);
        accountRepository.update(destinationAccount);

        InternalTransfer savedTransfer =
                internalTransferRepository.save(transfer);

        LedgerEntry debitEntry = new LedgerEntry(
                UUID.randomUUID(),
                transferId,
                correlationId,
                sourceAccount.getId(),
                command.amount(),
                debitResult.balanceBefore(),
                debitResult.balanceAfter(),
                LedgerEntryType.DEBIT,
                LedgerEntryStatus.COMPLETED,
                now);

        LedgerEntry creditEntry = new LedgerEntry(
                UUID.randomUUID(),
                transferId,
                correlationId,
                destinationAccount.getId(),
                command.amount(),
                creditResult.balanceBefore(),
                creditResult.balanceAfter(),
                LedgerEntryType.CREDIT,
                LedgerEntryStatus.COMPLETED,
                now);

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        return new InternalTransferResult(savedTransfer, false);
    }

    private Optional<InternalTransferResult> findExistingResult(
            String ownerSubject,
            String idempotencyKey,
            String requestFingerprint) {

        return internalTransferRepository
                .findByOwnerSubjectAndIdempotencyKey(
                        ownerSubject,
                        idempotencyKey)
                .map(existing -> toReplayResult(
                        existing,
                        requestFingerprint));
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
