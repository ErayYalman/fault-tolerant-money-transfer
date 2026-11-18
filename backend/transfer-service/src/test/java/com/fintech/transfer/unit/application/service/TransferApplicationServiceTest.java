package com.fintech.transfer.unit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.fintech.transfer.application.service.TransferApplicationService;
import com.fintech.transfer.application.service.TransferCreationService;
import com.fintech.transfer.application.service.TransferRequestFingerprint;
import com.fintech.transfer.domain.exception.IdempotencyConflictException;
import com.fintech.transfer.domain.exception.RecipientAccountNotActiveException;
import com.fintech.transfer.domain.exception.RecipientCurrencyMismatchException;
import com.fintech.transfer.domain.exception.TransferNotFoundException;
import com.fintech.transfer.domain.model.Money;
import com.fintech.transfer.domain.model.RecipientAccount;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.model.TransferStatus;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.domain.port.in.LookupRecipientAccountUseCase;
import com.fintech.transfer.domain.port.out.TransferRepository;

@ExtendWith(MockitoExtension.class)
class TransferApplicationServiceTest {

        @Mock
        private TransferRepository transferRepository;

        @Mock
        private TransferCreationService transferCreationService;

        @Mock
        private LookupRecipientAccountUseCase recipientAccountLookup;

        private TransferApplicationService service;

        @BeforeEach
        void setUp() {
                service = new TransferApplicationService(
                                transferRepository,
                                transferCreationService,
                                recipientAccountLookup);
        }

        @Test
        void shouldCreateTransferWhenIdempotencyKeyDoesNotExist() {
                UUID fromAccountId = UUID.randomUUID();
                UUID recipientId = UUID.randomUUID();

                InitiateTransferUseCase.InitiateTransferCommand command = new InitiateTransferUseCase.InitiateTransferCommand(
                                new BigDecimal("1500.00"),
                                "TRY",
                                fromAccountId,
                                " BANK-B-RECIPIENT-001 ",
                                "unique-key",
                                "customer-user-1");

                Transfer transfer = createTransfer(
                                fromAccountId,
                                recipientId,
                                "unique-key",
                                "a".repeat(64));

                when(transferRepository.findByOwnerSubjectAndIdempotencyKey("customer-user-1", "unique-key"))
                                .thenReturn(Optional.empty());

                when(recipientAccountLookup.findByAccountNumber(
                                " BANK-B-RECIPIENT-001 "))
                                .thenReturn(new RecipientAccount(
                                                recipientId,
                                                "BANK-B-RECIPIENT-001",
                                                "TRY",
                                                "ACTIVE"));

                when(transferCreationService.create(command, recipientId))
                                .thenReturn(transfer);

                InitiateTransferUseCase.CreateTransferResult result = service.execute(command);

                assertThat(result.created())
                                .isTrue();

                assertThat(result.transfer())
                                .isSameAs(transfer);

                verify(transferCreationService)
                                .create(command, recipientId);
        }

        @Test
        void shouldRejectInactiveRecipientBeforeCreatingTransfer() {
                UUID fromAccountId = UUID.randomUUID();

                InitiateTransferUseCase.InitiateTransferCommand command = new InitiateTransferUseCase.InitiateTransferCommand(
                                new BigDecimal("1500.00"),
                                "TRY",
                                fromAccountId,
                                " BANK-B-RECIPIENT-001 ",
                                "inactive-recipient-key",
                                "customer-user-1");

                when(transferRepository.findByOwnerSubjectAndIdempotencyKey("customer-user-1",
                                "inactive-recipient-key"))
                                .thenReturn(Optional.empty());

                when(recipientAccountLookup.findByAccountNumber(
                                " BANK-B-RECIPIENT-001 "))
                                .thenReturn(new RecipientAccount(
                                                UUID.randomUUID(),
                                                "BANK-B-RECIPIENT-001",
                                                "TRY",
                                                "FROZEN"));

                assertThatThrownBy(() -> service.execute(command))
                                .isInstanceOf(RecipientAccountNotActiveException.class);

                verifyNoInteractions(transferCreationService);
        }

        @Test
        void shouldRejectRecipientWithDifferentCurrency() {
                UUID fromAccountId = UUID.randomUUID();

                InitiateTransferUseCase.InitiateTransferCommand command = new InitiateTransferUseCase.InitiateTransferCommand(
                                new BigDecimal("1500.00"),
                                "TRY",
                                fromAccountId,
                                " BANK-B-RECIPIENT-001 ",
                                "currency-mismatch-key",
                                "customer-user-1");

                when(transferRepository.findByOwnerSubjectAndIdempotencyKey("customer-user-1",
                                "currency-mismatch-key"))
                                .thenReturn(Optional.empty());

                when(recipientAccountLookup.findByAccountNumber(
                                " BANK-B-RECIPIENT-001 "))
                                .thenReturn(new RecipientAccount(
                                                UUID.randomUUID(),
                                                "BANK-B-RECIPIENT-001",
                                                "USD",
                                                "ACTIVE"));

                assertThatThrownBy(() -> service.execute(command))
                                .isInstanceOf(RecipientCurrencyMismatchException.class);

                verifyNoInteractions(transferCreationService);
        }

        @Test
        void shouldReturnExistingTransferForDuplicateIdempotencyKey() {
                UUID fromAccountId = UUID.randomUUID();
                UUID toAccountId = UUID.randomUUID();

                InitiateTransferUseCase.InitiateTransferCommand command = new InitiateTransferUseCase.InitiateTransferCommand(
                                new BigDecimal("1500.00"),
                                "TRY",
                                fromAccountId,
                                " BANK-B-RECIPIENT-001 ",
                                "existing-key",
                                "customer-user-1");

                Transfer existingTransfer = createTransfer(
                                fromAccountId,
                                toAccountId,
                                "existing-key",
                                TransferRequestFingerprint.from(command));

                when(transferRepository.findByOwnerSubjectAndIdempotencyKey("customer-user-1", "existing-key"))
                                .thenReturn(Optional.of(existingTransfer));

                InitiateTransferUseCase.CreateTransferResult result = service.execute(command);

                assertThat(result.created())
                                .isFalse();

                assertThat(result.transfer())
                                .isSameAs(existingTransfer);

                verifyNoInteractions(
                                transferCreationService,
                                recipientAccountLookup);
        }

        @Test
        void shouldRejectSameIdempotencyKeyWithDifferentFingerprint() {
                UUID fromAccountId = UUID.randomUUID();

                InitiateTransferUseCase.InitiateTransferCommand originalCommand = new InitiateTransferUseCase.InitiateTransferCommand(
                                new BigDecimal("1500.00"),
                                "TRY",
                                fromAccountId,
                                " BANK-B-RECIPIENT-001 ",
                                "conflict-key",
                                "customer-user-1");

                Transfer existingTransfer = createTransfer(
                                fromAccountId,
                                UUID.randomUUID(),
                                "conflict-key",
                                TransferRequestFingerprint.from(originalCommand));

                when(transferRepository.findByOwnerSubjectAndIdempotencyKey("customer-user-1", "conflict-key"))
                                .thenReturn(Optional.of(existingTransfer));

                InitiateTransferUseCase.InitiateTransferCommand differentCommand = new InitiateTransferUseCase.InitiateTransferCommand(
                                new BigDecimal("1000.00"),
                                "TRY",
                                fromAccountId,
                                " BANK-B-RECIPIENT-001 ",
                                "conflict-key",
                                "customer-user-1");

                assertThatThrownBy(() -> service.execute(differentCommand))
                                .isInstanceOf(IdempotencyConflictException.class);

                verifyNoInteractions(
                                transferCreationService,
                                recipientAccountLookup);
        }

        @Test
        void shouldReturnExistingTransferWhenStoredFingerprintIsMissing() {
                UUID fromAccountId = UUID.randomUUID();
                UUID toAccountId = UUID.randomUUID();
                Instant now = Instant.now();

                InitiateTransferUseCase.InitiateTransferCommand command = new InitiateTransferUseCase.InitiateTransferCommand(
                                new BigDecimal("1500.00"),
                                "TRY",
                                fromAccountId,
                                " BANK-B-RECIPIENT-001 ",
                                "legacy-key",
                                "customer-user-1");

                Transfer existingTransfer = Transfer.rehydrate(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "legacy-key",
                                "customer-user-1",
                                fromAccountId,
                                toAccountId,
                                new Money(
                                                new BigDecimal("1500.00"),
                                                "TRY"),
                                TransferStatus.PENDING,
                                null,
                                now,
                                null,
                                null,
                                null,
                                null,
                                null,
                                now,
                                now,
                                null);

                when(transferRepository.findByOwnerSubjectAndIdempotencyKey("customer-user-1", "legacy-key"))
                                .thenReturn(Optional.of(existingTransfer));

                InitiateTransferUseCase.CreateTransferResult result = service.execute(command);

                assertThat(result.created())
                                .isFalse();

                assertThat(result.transfer())
                                .isSameAs(existingTransfer);

                verifyNoInteractions(
                                transferCreationService,
                                recipientAccountLookup);
        }

        @Test
        void shouldRejectConcurrentInsertWithDifferentFingerprint() {
                UUID fromAccountId = UUID.randomUUID();
                UUID recipientId = UUID.randomUUID();

                InitiateTransferUseCase.InitiateTransferCommand originalCommand = new InitiateTransferUseCase.InitiateTransferCommand(
                                new BigDecimal("1500.00"),
                                "TRY",
                                fromAccountId,
                                " BANK-B-RECIPIENT-001 ",
                                "concurrent-key",
                                "customer-user-1");

                Transfer existingTransfer = createTransfer(
                                fromAccountId,
                                recipientId,
                                "concurrent-key",
                                TransferRequestFingerprint.from(originalCommand));

                InitiateTransferUseCase.InitiateTransferCommand differentCommand = new InitiateTransferUseCase.InitiateTransferCommand(
                                new BigDecimal("1000.00"),
                                "TRY",
                                fromAccountId,
                                " BANK-B-RECIPIENT-001 ",
                                "concurrent-key",
                                "customer-user-1");

                when(transferRepository.findByOwnerSubjectAndIdempotencyKey("customer-user-1", "concurrent-key"))
                                .thenReturn(Optional.empty())
                                .thenReturn(Optional.of(existingTransfer));

                when(recipientAccountLookup.findByAccountNumber(
                                " BANK-B-RECIPIENT-001 "))
                                .thenReturn(new RecipientAccount(
                                                recipientId,
                                                "BANK-B-RECIPIENT-001",
                                                "TRY",
                                                "ACTIVE"));

                when(transferCreationService.create(differentCommand, recipientId))
                                .thenThrow(new DataIntegrityViolationException("duplicate"));

                assertThatThrownBy(() -> service.execute(differentCommand))
                                .isInstanceOf(IdempotencyConflictException.class);
        }

        @Test
        void shouldReturnTransferById() {
                UUID transferId = UUID.randomUUID();
                String ownerSubject = "customer-user-1";

                Transfer transfer = Transfer.create(
                                transferId,
                                UUID.randomUUID(),
                                "get-key",
                                ownerSubject,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                new Money(
                                                new BigDecimal("100.00"),
                                                "TRY"),
                                "a".repeat(64),
                                Instant.now());

                when(transferRepository.findByIdAndOwnerSubject(transferId, ownerSubject))
                                .thenReturn(Optional.of(transfer));

                Transfer result = service.getById(transferId, ownerSubject);

                assertThat(result)
                                .isSameAs(transfer);
        }

        @Test
        void shouldThrowWhenTransferDoesNotExist() {
                UUID transferId = UUID.randomUUID();
                String ownerSubject = "customer-user-1";

                when(transferRepository.findByIdAndOwnerSubject(transferId, ownerSubject))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.getById(transferId, ownerSubject))
                                .isInstanceOf(TransferNotFoundException.class);

                verify(transferRepository)
                                .findByIdAndOwnerSubject(transferId, ownerSubject);
        }

        @Test
        void shouldThrowWhenTransferBelongsToDifferentOwner() {
                UUID transferId = UUID.randomUUID();
                String requesterSubject = "customer-user-2";

                when(transferRepository.findByIdAndOwnerSubject(transferId, requesterSubject))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.getById(transferId, requesterSubject))
                                .isInstanceOf(TransferNotFoundException.class);

                verify(transferRepository)
                                .findByIdAndOwnerSubject(transferId, requesterSubject);
        }

        private Transfer createTransfer(
                        UUID fromAccountId,
                        UUID toAccountId,
                        String idempotencyKey,
                        String requestFingerprint) {
                return Transfer.create(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                idempotencyKey,
                                "customer-user-1",
                                fromAccountId,
                                toAccountId,
                                new Money(
                                                new BigDecimal("1500.00"),
                                                "TRY"),
                                requestFingerprint,
                                Instant.now());
        }
}