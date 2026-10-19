package com.fintech.payment.a.application.port.out;

import com.fintech.payment.a.application.messaging.PendingOutboxMessage;

public interface OutboxMessageTransport {

    void publish(PendingOutboxMessage message);
}