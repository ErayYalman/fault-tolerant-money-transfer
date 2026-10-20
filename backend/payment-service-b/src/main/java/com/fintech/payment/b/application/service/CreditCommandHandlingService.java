package com.fintech.payment.b.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.fintech.payment.b.application.messaging.CommandFingerprint;
import com.fintech.payment.b.application.messaging.ConsumedCommand;
import com.fintech.payment.b.application.messaging.OutboundMessage;
import com.fintech.payment.b.application.messaging.command.CreditRequestedCommand;
import com.fintech.payment.b.application.messaging.event.CreditCompletedEvent;
import com.fintech.payment.b.application.messaging.event.CreditFailedEvent;
import com.fintech.payment.b.application.port.out.ConsumedCommandInbox;
import com.fintech.payment.b.application.port.out.PaymentEventPublisher;
import com.fintech.payment.b.domain.exception.AccountNotActiveException;
import com.fintech.payment.b.domain.exception.CurrencyMismatchException;
import com.fintech.payment.b.domain.model.Account;
import com.fintech.payment.b.domain.model.LedgerEntry;
import com.fintech.payment.b.domain.model.LedgerEntryStatus;
import com.fintech.payment.b.domain.model.LedgerEntryType;
import com.fintech.payment.b.domain.port.out.AccountRepository;
import com.fintech.payment.b.domain.port.out.LedgerEntryRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CreditCommandHandlingService {

    private static final String COMMAND_TYPE = "credit-requested";
    private static final String AGGREGATE_TYPE = "TRANSFER";

    private final ConsumedCommandInbox commandInbox;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentEventPublisher eventPublisher;
    private final Clock clock;

    public CreditCommandHandlingService(
            ConsumedCommandInbox commandInbox,
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PaymentEventPublisher eventPublisher,
            Clock clock) {
        this.commandInbox = commandInbox;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Result handle(CreditRequestedCommand command) {
        String requestHash = CommandFingerprint.forCredit(command);

        ConsumedCommand consumedCommand = new ConsumedCommand(
                command.messageId(),
                command.idempotencyKey(),
                command.transferId(),
                command.correlationId(),
                COMMAND_TYPE,
                requestHash);

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

        Account.CreditResult creditResult;

        try {
            creditResult = account.credit(
                    command.amount(),
                    command.currency(),
                    now);
        } catch (AccountNotActiveException exception) {
            publishFailure(command, "ACCOUNT_NOT_ACTIVE", now);
            return Result.BUSINESS_FAILURE;
        } catch (CurrencyMismatchException exception) {
            publishFailure(command, "CURRENCY_MISMATCH", now);
            return Result.BUSINESS_FAILURE;
        } catch (IllegalArgumentException exception) {
            // Account.credit throws this for an invalid ISO currency code.
            publishFailure(command, "INVALID_CURRENCY", now);
            return Result.BUSINESS_FAILURE;
        }

        /*
         * All successful-credit writes participate in this transaction:
         * account update + ledger entry + Outbox event.
         *
         * A failure while saving any of them must roll back the entire
         * operation, including the Inbox registration.
         */
        accountRepository.update(account);

        UUID ledgerEntryId = UUID.randomUUID();

        LedgerEntry ledgerEntry = new LedgerEntry(
                ledgerEntryId,
                command.transferId(),
                command.correlationId(),
                command.accountId(),
                command.amount(),
                creditResult.balanceBefore(),
                creditResult.balanceAfter(),
                LedgerEntryType.CREDIT,
                LedgerEntryStatus.COMPLETED,
                now);

        ledgerEntryRepository.save(ledgerEntry);

        CreditCompletedEvent event = new CreditCompletedEvent(
                UUID.randomUUID(),
                command.correlationId(),
                ledgerEntryId,
                now);

        eventPublisher.append(
                new OutboundMessage(
                        AGGREGATE_TYPE,
                        command.transferId(),
                        "credit-completed",
                        event.toPayload()));

        return Result.PROCESSED;
    }

    private void publishFailure(
            CreditRequestedCommand command,
            String failureReason,
            Instant failedAt) {
        CreditFailedEvent event = new CreditFailedEvent(
                UUID.randomUUID(),
                command.correlationId(),
                failureReason,
                failedAt);

        eventPublisher.append(
                new OutboundMessage(
                        AGGREGATE_TYPE,
                        command.transferId(),
                        "credit-failed",
                        event.toPayload()));
    }

    public enum Result {
        PROCESSED,
        BUSINESS_FAILURE,
        DUPLICATE
    }
}