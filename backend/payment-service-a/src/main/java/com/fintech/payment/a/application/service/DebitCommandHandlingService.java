package com.fintech.payment.a.application.service;

import com.fintech.payment.a.application.messaging.CommandFingerprint;
import com.fintech.payment.a.application.messaging.ConsumedCommand;
import com.fintech.payment.a.application.messaging.OutboundMessage;
import com.fintech.payment.a.application.messaging.command.DebitRequestedCommand;
import com.fintech.payment.a.application.messaging.event.DebitCompletedEvent;
import com.fintech.payment.a.application.messaging.event.DebitFailedEvent;
import com.fintech.payment.a.application.port.out.ConsumedCommandInbox;
import com.fintech.payment.a.application.port.out.PaymentEventPublisher;
import com.fintech.payment.a.domain.exception.AccountNotActiveException;
import com.fintech.payment.a.domain.exception.CurrencyMismatchException;
import com.fintech.payment.a.domain.exception.InsufficientFundsException;
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
public class DebitCommandHandlingService {

    private static final String COMMAND_TYPE = "debit-requested";
    private static final String AGGREGATE_TYPE = "TRANSFER";

    private final ConsumedCommandInbox commandInbox;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentEventPublisher eventPublisher;
    private final Clock clock;

    public DebitCommandHandlingService(
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
    public Result handle(DebitRequestedCommand command) {
        String requestHash = CommandFingerprint.forDebit(command);

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

        Instant now = Instant.now(clock);

        Account account = accountRepository.findById(command.accountId())
                .orElse(null);

        if (account == null) {
            publishFailure(command, "ACCOUNT_NOT_FOUND", now);
            return Result.BUSINESS_FAILURE;
        }

        Account.DebitResult debitResult;

        try {
            debitResult = account.debit(
                    command.amount(),
                    command.currency(),
                    now
            );
        } catch (InsufficientFundsException exception) {
            publishFailure(command, "INSUFFICIENT_FUNDS", now);
            return Result.BUSINESS_FAILURE;
        } catch (AccountNotActiveException exception) {
            publishFailure(command, "ACCOUNT_NOT_ACTIVE", now);
            return Result.BUSINESS_FAILURE;
        } catch (CurrencyMismatchException exception) {
            publishFailure(command, "CURRENCY_MISMATCH", now);
            return Result.BUSINESS_FAILURE;
        } catch (IllegalArgumentException exception) {
            // Account.debit() uses this for an invalid ISO currency code.
            publishFailure(command, "INVALID_CURRENCY", now);
            return Result.BUSINESS_FAILURE;
        }

        /*
         * These writes join this service's transaction:
         * account update + ledger + Outbox.
         *
         * A failure in any of them rolls back the complete debit operation,
         * including the Inbox registration made above.
         */
        accountRepository.update(account);

        UUID ledgerEntryId = UUID.randomUUID();

        LedgerEntry ledgerEntry = new LedgerEntry(
                ledgerEntryId,
                command.transferId(),
                command.correlationId(),
                command.accountId(),
                command.amount(),
                debitResult.balanceBefore(),
                debitResult.balanceAfter(),
                LedgerEntryType.DEBIT,
                LedgerEntryStatus.COMPLETED,
                now
        );

        ledgerEntryRepository.save(ledgerEntry);

        DebitCompletedEvent event = new DebitCompletedEvent(
                UUID.randomUUID(),
                command.correlationId(),
                ledgerEntryId,
                now
        );

        eventPublisher.append(
                new OutboundMessage(
                        AGGREGATE_TYPE,
                        command.transferId(),
                        "debit-completed",
                        event.toPayload()
                )
        );

        return Result.PROCESSED;
    }

    private void publishFailure(
            DebitRequestedCommand command,
            String failureReason,
            Instant failedAt
    ) {
        DebitFailedEvent event = new DebitFailedEvent(
                UUID.randomUUID(),
                command.correlationId(),
                failureReason,
                failedAt
        );

        eventPublisher.append(
                new OutboundMessage(
                        AGGREGATE_TYPE,
                        command.transferId(),
                        "debit-failed",
                        event.toPayload()
                )
        );
    }

    public enum Result {
        PROCESSED,
        BUSINESS_FAILURE,
        DUPLICATE
    }
}