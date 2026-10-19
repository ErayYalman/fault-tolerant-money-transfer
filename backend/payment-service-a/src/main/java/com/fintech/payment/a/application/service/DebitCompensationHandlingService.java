package com.fintech.payment.a.application.service;

import com.fintech.payment.a.application.exception.CompensationInvariantViolationException;
import com.fintech.payment.a.application.messaging.CommandFingerprint;
import com.fintech.payment.a.application.messaging.ConsumedCommand;
import com.fintech.payment.a.application.messaging.OutboundMessage;
import com.fintech.payment.a.application.messaging.command.DebitCompensationCommand;
import com.fintech.payment.a.application.messaging.event.DebitCompensatedEvent;
import com.fintech.payment.a.application.port.out.ConsumedCommandInbox;
import com.fintech.payment.a.application.port.out.PaymentEventPublisher;
import com.fintech.payment.a.domain.model.Account;
import com.fintech.payment.a.domain.model.LedgerEntry;
import com.fintech.payment.a.domain.model.LedgerEntryStatus;
import com.fintech.payment.a.domain.model.LedgerEntryType;
import com.fintech.payment.a.domain.port.out.AccountRepository;
import com.fintech.payment.a.domain.port.out.LedgerEntryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class DebitCompensationHandlingService {

    private static final String COMMAND_TYPE = "debit-compensate";
    private static final String AGGREGATE_TYPE = "TRANSFER";

    private final ConsumedCommandInbox commandInbox;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentEventPublisher eventPublisher;
    private final Clock clock;

    public DebitCompensationHandlingService(
            ConsumedCommandInbox commandInbox,
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PaymentEventPublisher eventPublisher,
            Clock clock
    ) {
        this.commandInbox = commandInbox;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Result handle(DebitCompensationCommand command) {
        String requestHash =
                CommandFingerprint.forDebitCompensation(command);

        ConsumedCommand consumedCommand = new ConsumedCommand(
                command.messageId(),
                command.idempotencyKey(),
                command.transferId(),
                command.correlationId(),
                COMMAND_TYPE,
                requestHash
        );

        if (!commandInbox.tryRegister(consumedCommand)) {
            return Result.DUPLICATE;
        }

        /*
         * Compensation must be backed by an already successful debit.
         * Missing or mismatched ledger state is an invariant violation, not
         * a normal business rejection: no success event may be emitted.
         */
        LedgerEntry originalDebit =
                ledgerEntryRepository.findByCorrelationIdAndEntryType(
                                command.correlationId(),
                                LedgerEntryType.DEBIT
                        )
                        .orElseThrow(() ->
                                new CompensationInvariantViolationException(
                                        "Cannot compensate because the original " +
                                                "debit ledger entry was not found"
                                )
                        );

        validateOriginalDebit(command, originalDebit);

        Account account = accountRepository.findById(command.accountId())
                .orElseThrow(() ->
                        new CompensationInvariantViolationException(
                                "Original debit exists, but the account was not found"
                        )
                );

        Instant now = Instant.now(clock);

        Account.ReversalResult reversalResult = account.reverseDebit(
                command.amount(),
                command.currency(),
                now
        );

        /*
         * All writes below participate in the same transaction:
         * account balance + reversal ledger + debit-compensated Outbox.
         */
        accountRepository.update(account);

        UUID reversalLedgerEntryId = UUID.randomUUID();

        LedgerEntry reversalEntry = new LedgerEntry(
                reversalLedgerEntryId,
                command.transferId(),
                command.correlationId(),
                command.accountId(),
                command.amount(),
                reversalResult.balanceBefore(),
                reversalResult.balanceAfter(),
                LedgerEntryType.REVERSAL,
                LedgerEntryStatus.COMPLETED,
                now
        );

        ledgerEntryRepository.save(reversalEntry);

        DebitCompensatedEvent event = new DebitCompensatedEvent(
                UUID.randomUUID(),
                command.correlationId(),
                reversalLedgerEntryId,
                now
        );

        eventPublisher.append(
                new OutboundMessage(
                        AGGREGATE_TYPE,
                        command.transferId(),
                        "debit-compensated",
                        event.toPayload()
                )
        );

        return Result.PROCESSED;
    }

    private void validateOriginalDebit(
            DebitCompensationCommand command,
            LedgerEntry originalDebit
    ) {
        boolean matches =
                originalDebit.entryType() == LedgerEntryType.DEBIT
                && originalDebit.status() == LedgerEntryStatus.COMPLETED
                && originalDebit.transferId().equals(command.transferId())
                && originalDebit.correlationId().equals(command.correlationId())
                && originalDebit.accountId().equals(command.accountId())
                && originalDebit.amount().compareTo(command.amount()) == 0;

        if (!matches) {
            throw new CompensationInvariantViolationException(
                    "Compensation command does not match the original debit"
            );
        }
    }

    public enum Result {
        PROCESSED,
        DUPLICATE
    }
}