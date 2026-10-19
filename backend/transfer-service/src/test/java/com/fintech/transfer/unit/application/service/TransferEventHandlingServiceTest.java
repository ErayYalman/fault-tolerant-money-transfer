package com.fintech.transfer.unit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fintech.transfer.application.messaging.OutboundMessage;
import com.fintech.transfer.application.messaging.event.DebitCompletedEvent;
import com.fintech.transfer.application.port.out.ConsumedEventInbox;
import com.fintech.transfer.application.port.out.DomainEventPublisher;
import com.fintech.transfer.application.saga.SagaAction;
import com.fintech.transfer.application.saga.SagaDecision;
import com.fintech.transfer.application.saga.TransferSagaOrchestrator;
import com.fintech.transfer.application.service.TransferEventHandlingService;
import com.fintech.transfer.domain.model.Money;
import com.fintech.transfer.domain.model.Transfer;

class TransferEventHandlingServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-15T09:00:00Z");

    private static final Instant DEBIT_PROCESSED_AT = Instant.parse("2026-10-15T09:00:02Z");

    private static final Instant CREDIT_REQUESTED_AT = Instant.parse("2026-10-15T09:00:03Z");

    private ConsumedEventInbox consumedEventInbox;
    private TransferSagaOrchestrator sagaOrchestrator;
    private DomainEventPublisher domainEventPublisher;

    private TransferEventHandlingService service;

    @BeforeEach
    void setUp() {
        consumedEventInbox = mock(ConsumedEventInbox.class);
        sagaOrchestrator = mock(TransferSagaOrchestrator.class);
        domainEventPublisher = mock(DomainEventPublisher.class);

        Clock clock = Clock.fixed(
                CREDIT_REQUESTED_AT,
                ZoneOffset.UTC);

        service = new TransferEventHandlingService(
                consumedEventInbox,
                sagaOrchestrator,
                domainEventPublisher,
                clock);
    }

    @Test
    void shouldPublishCreditCommandAfterDebitCompleted() {
        UUID eventId = UUID.randomUUID();

        Transfer transfer = createCreditRequestedTransfer();
        UUID correlationId = transfer.getCorrelationId();

        DebitCompletedEvent event = new DebitCompletedEvent(
                eventId,
                correlationId,
                UUID.randomUUID(),
                DEBIT_PROCESSED_AT);

        when(consumedEventInbox.tryRegister(
                eventId,
                "debit-completed",
                correlationId)).thenReturn(true);

        when(sagaOrchestrator.onDebitCompleted(
                correlationId,
                DEBIT_PROCESSED_AT,
                CREDIT_REQUESTED_AT)).thenReturn(
                        new SagaDecision(
                                SagaAction.REQUEST_CREDIT,
                                transfer));

        boolean processed = service.handle(event);

        assertThat(processed).isTrue();

        verify(consumedEventInbox).tryRegister(
                eventId,
                "debit-completed",
                correlationId);

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);

        verify(domainEventPublisher).publish(captor.capture());

        OutboundMessage message = captor.getValue();

        assertThat(message.aggregateType()).isEqualTo("TRANSFER");
        assertThat(message.aggregateId()).isEqualTo(transfer.getId());
        assertThat(message.eventType()).isEqualTo("credit-requested");

        assertThat(message.payload())
                .containsEntry(
                        "transferId",
                        transfer.getId().toString())
                .containsEntry(
                        "correlationId",
                        correlationId.toString())
                .containsEntry(
                        "accountId",
                        transfer.getToAccountId().toString())
                .containsEntry("currency", "TRY")
                .containsEntry(
                        "amount",
                        new BigDecimal("1000.00"))
                // EKLENDİ: rastgele üretilen alanlar en azından dolu olmalı
                .containsKey("messageId")
                .containsKey("idempotencyKey");
    }

    @Test
    void shouldIgnoreDuplicateDebitCompletedEvent() {
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        DebitCompletedEvent event = new DebitCompletedEvent(
                eventId,
                correlationId,
                UUID.randomUUID(),
                DEBIT_PROCESSED_AT);

        when(consumedEventInbox.tryRegister(
                eventId,
                "debit-completed",
                correlationId)).thenReturn(false);

        boolean processed = service.handle(event);

        assertThat(processed).isFalse();

        verifyNoInteractions(
                sagaOrchestrator,
                domainEventPublisher);
    }

    private Transfer createCreditRequestedTransfer() {
        Transfer transfer = Transfer.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test-" + UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Money(
                        new BigDecimal("1000.00"),
                        "TRY"),
                CREATED_AT);

        transfer.requestDebit(
                CREATED_AT.plusSeconds(1));
        transfer.completeDebit(DEBIT_PROCESSED_AT);
        transfer.requestCredit(CREDIT_REQUESTED_AT);

        return transfer;
    }
}