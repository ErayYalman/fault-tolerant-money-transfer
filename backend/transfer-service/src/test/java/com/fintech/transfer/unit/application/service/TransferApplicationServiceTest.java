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

import com.fintech.transfer.application.service.TransferApplicationService;
import com.fintech.transfer.application.service.TransferCreationService;
import com.fintech.transfer.domain.exception.TransferNotFoundException;
import com.fintech.transfer.domain.model.Money;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.domain.port.out.TransferRepository;

@ExtendWith(MockitoExtension.class)
class TransferApplicationServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private TransferCreationService transferCreationService;

    private TransferApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TransferApplicationService(
                transferRepository,
                transferCreationService);
    }

    @Test
    void shouldCreateTransferWhenIdempotencyKeyDoesNotExist() {
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();

        InitiateTransferUseCase.InitiateTransferCommand command = new InitiateTransferUseCase.InitiateTransferCommand(
                new BigDecimal("1500.00"),
                "TRY",
                fromAccountId,
                toAccountId,
                "unique-key");

        Transfer transfer = createTransfer(
                fromAccountId,
                toAccountId,
                "unique-key");

        when(transferRepository.findByIdempotencyKey("unique-key"))
                .thenReturn(Optional.empty());

        when(transferCreationService.create(command))
                .thenReturn(transfer);

        InitiateTransferUseCase.CreateTransferResult result = service.execute(command);

        assertThat(result.created())
                .isTrue();

        assertThat(result.transfer())
                .isSameAs(transfer);

        verify(transferCreationService)
                .create(command);
    }

    @Test
    void shouldReturnExistingTransferForDuplicateIdempotencyKey() {
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();

        InitiateTransferUseCase.InitiateTransferCommand command = new InitiateTransferUseCase.InitiateTransferCommand(
                new BigDecimal("1500.00"),
                "TRY",
                fromAccountId,
                toAccountId,
                "existing-key");

        Transfer existingTransfer = createTransfer(
                fromAccountId,
                toAccountId,
                "existing-key");

        when(transferRepository.findByIdempotencyKey("existing-key"))
                .thenReturn(Optional.of(existingTransfer));

        InitiateTransferUseCase.CreateTransferResult result = service.execute(command);

        assertThat(result.created())
                .isFalse();

        assertThat(result.transfer())
                .isSameAs(existingTransfer);

        verifyNoInteractions(transferCreationService);
    }

    @Test
    void shouldReturnTransferById() {
        UUID transferId = UUID.randomUUID();

        Transfer transfer = Transfer.create(
                transferId,
                UUID.randomUUID(),
                "get-key",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Money(
                        new BigDecimal("100.00"),
                        "TRY"),
                Instant.now());

        when(transferRepository.findById(transferId))
                .thenReturn(Optional.of(transfer));

        Transfer result = service.getById(transferId);

        assertThat(result)
                .isSameAs(transfer);
    }

    @Test
    void shouldThrowWhenTransferDoesNotExist() {
        UUID transferId = UUID.randomUUID();

        when(transferRepository.findById(transferId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(transferId))
                .isInstanceOf(
                        
                    TransferNotFoundException.class);

        verify(transferRepository)
                .findById(transferId);
    }

    private Transfer createTransfer(
            UUID fromAccountId,
            UUID toAccountId,
            String idempotencyKey) {
        return Transfer.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                idempotencyKey,
                fromAccountId,
                toAccountId,
                new Money(
                        new BigDecimal("1500.00"),
                        "TRY"),
                Instant.now());
    }
}