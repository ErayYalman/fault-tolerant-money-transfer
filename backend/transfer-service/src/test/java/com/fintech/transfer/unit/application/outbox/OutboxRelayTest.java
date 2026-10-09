package com.fintech.transfer.unit.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fintech.transfer.application.messaging.PendingOutboxMessage;
import com.fintech.transfer.application.outbox.OutboxRelay;
import com.fintech.transfer.application.port.out.OutboxMessageTransport;
import com.fintech.transfer.application.port.out.OutboxRelayStore;

class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    @Test
    void shouldPublishAndMarkMessagesInOrder() {
        OutboxRelayStore store = mock(OutboxRelayStore.class);
        OutboxMessageTransport transport = mock(OutboxMessageTransport.class);

        PendingOutboxMessage first = message(NOW);
        PendingOutboxMessage second = message(NOW.plusSeconds(1));

        when(store.findPendingBatch(10))
                .thenReturn(List.of(first, second));

        OutboxRelay relay = new OutboxRelay(
                store,
                transport,
                Clock.fixed(NOW, ZoneOffset.UTC));

        int published = relay.publishPendingBatch(10);

        assertThat(published).isEqualTo(2);

        var inOrder = inOrder(transport, store);

        inOrder.verify(transport).publish(first);
        inOrder.verify(store).markPublished(first.id(), NOW);

        inOrder.verify(transport).publish(second);
        inOrder.verify(store).markPublished(second.id(), NOW);
    }

    @Test
    void shouldStopBatchWhenPublicationFails() {
        OutboxRelayStore store = mock(OutboxRelayStore.class);
        OutboxMessageTransport transport = mock(OutboxMessageTransport.class);

        PendingOutboxMessage first = message(NOW);
        PendingOutboxMessage second = message(NOW.plusSeconds(1));
        PendingOutboxMessage third = message(NOW.plusSeconds(2));

        when(store.findPendingBatch(10))
                .thenReturn(List.of(first, second, third));

        doThrow(new IllegalStateException("Broker unavailable"))
                .when(transport)
                .publish(second);

        OutboxRelay relay = new OutboxRelay(
                store,
                transport,
                Clock.fixed(NOW, ZoneOffset.UTC));

        int published = relay.publishPendingBatch(10);

        assertThat(published).isEqualTo(1);

        verify(transport).publish(first);
        verify(transport).publish(second);
        verify(transport, never()).publish(third);

        verify(store).markPublished(first.id(), NOW);
        verify(store, never()).markPublished(second.id(), NOW);
        verify(store, never()).markPublished(third.id(), NOW);
    }

    private PendingOutboxMessage message(Instant createdAt) {
        return new PendingOutboxMessage(
                UUID.randomUUID(),
                "TRANSFER",
                UUID.randomUUID(),
                "test-message",
                Map.of("messageId", UUID.randomUUID().toString()),
                createdAt);
    }
}