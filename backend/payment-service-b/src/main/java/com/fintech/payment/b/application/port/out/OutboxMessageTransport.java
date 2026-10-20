package com.fintech.payment.b.application.port.out;

import com.fintech.payment.b.application.messaging.PendingOutboxMessage;

public interface OutboxMessageTransport {

    void publish(PendingOutboxMessage message);
}